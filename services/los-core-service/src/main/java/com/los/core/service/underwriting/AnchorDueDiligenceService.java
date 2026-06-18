package com.los.core.service.underwriting;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.plp.service.PlpAnchorSanctionHookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anchor (invoice discounting) underwriting via due-diligence checklist and derived credit rating.
 * Replaces bureau/scorecard underwriting for anchor onboarding.
 */
@Service
@RequiredArgsConstructor
public class AnchorDueDiligenceService {

    static final List<String> QUESTION_KEYS = List.of(
            "externalCreditRating",
            "financialPerformance",
            "businessVintage",
            "gstCompliance",
            "industryRisk",
            "adverseNewsFlow",
            "legalLitigation",
            "managementTrackRecord"
    );

    private final LoanApplicationRepository applicationRepository;
    private final IKycOrchestrationService kycOrchestrationService;
    private final AuditService auditService;
    private final PlpAnchorSanctionHookService plpAnchorSanctionHookService;

    @Transactional(readOnly = true)
    public Map<String, Object> getDueDiligence(UUID applicationId) {
        LoanApplication app = findOrThrow(applicationId);
        assertAnchorFlow(app);
        return readDueDiligenceBlock(app);
    }

    @Transactional
    public Map<String, Object> saveDueDiligence(UUID applicationId, Map<String, Object> answers) {
        LoanApplication app = findOrThrow(applicationId);
        assertAnchorFlow(app);
        assertKycReady(app, applicationId);

        Map<String, Object> normalized = normalizeAnswers(answers);
        Map<String, Object> block = computeRatingBlock(normalized, false);
        mergeDueDiligence(app, block);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);

        auditService.logEvent(applicationId, "FLOW", "ANCHOR_DD_SAVED", null, null,
                Map.of("creditRating", String.valueOf(block.get("creditRating")),
                        "score", String.valueOf(block.get("score"))),
                "Anchor due diligence checklist saved");
        return block;
    }

    @Transactional
    public Map<String, Object> completeDueDiligenceUnderwriting(UUID applicationId) {
        LoanApplication app = findOrThrow(applicationId);
        assertAnchorFlow(app);
        assertKycReady(app, applicationId);

        Map<String, Object> block = readDueDiligenceBlock(app);
        @SuppressWarnings("unchecked")
        Map<String, Object> answers = (Map<String, Object>) block.get("answers");
        if (answers == null || answers.isEmpty()) {
            throw new BusinessRuleException(
                    "Complete the due diligence checklist before underwriting",
                    "ANCHOR_DD_INCOMPLETE",
                    "SAVE_DUE_DILIGENCE",
                    null);
        }
        for (String key : QUESTION_KEYS) {
            if (!answers.containsKey(key) || String.valueOf(answers.get(key)).isBlank()) {
                throw new BusinessRuleException(
                        "All due diligence questions must be answered",
                        "ANCHOR_DD_INCOMPLETE",
                        "SAVE_DUE_DILIGENCE",
                        Map.of("missing", key));
            }
        }

        block = computeRatingBlock(answers, true);
        mergeDueDiligence(app, block);
        String rating = String.valueOf(block.get("creditRating"));
        int score = ((Number) block.get("score")).intValue();

        app.setCreditRiskScore(score);
        app.setBureauScore(null);
        app.setManualBureauScore(null);

        if ("D".equals(rating)) {
            app.setCreditDecision("REJECTED");
            app.setStatus(ApplicationStatus.REJECTED);
            app.setUpdatedAt(Instant.now());
            applicationRepository.save(app);
            auditService.logEvent(applicationId, "FLOW", "ANCHOR_UW_COMPLETE", null, null,
                    Map.of("decision", "REJECTED", "creditRating", rating, "score", String.valueOf(score)),
                    "Anchor underwriting rejected — credit rating D");
            return underwritingResult(app, block, "REJECTED");
        }

        if ("C".equals(rating)) {
            app.setCreditDecision("MANUAL_REVIEW");
            app.setStatus(ApplicationStatus.UNDERWRITING);
            app.setUpdatedAt(Instant.now());
            applicationRepository.save(app);
            auditService.logEvent(applicationId, "FLOW", "ANCHOR_UW_COMPLETE", null, null,
                    Map.of("decision", "MANUAL_REVIEW", "creditRating", rating, "score", String.valueOf(score)),
                    "Anchor underwriting referred — credit rating C");
            return underwritingResult(app, block, "MANUAL_REVIEW");
        }

        app.setCreditDecision("APPROVED");
        app.setStatus(ApplicationStatus.SANCTION_PENDING);
        if (app.getSanctionedAmount() == null && app.getRequestedAmount() != null) {
            app.setSanctionedAmount(app.getRequestedAmount());
        }
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);
        if ("APPROVED".equals(app.getCreditDecision())) {
            triggerPlpAnchorPush(applicationId);
        }
        auditService.logEvent(applicationId, "FLOW", "ANCHOR_UW_COMPLETE", null, null,
                Map.of("decision", "APPROVED", "creditRating", rating, "score", String.valueOf(score),
                        "status", "SANCTION_PENDING"),
                "Anchor underwriting approved — proceed to sanction");
        return underwritingResult(app, block, "APPROVED");
    }

    @Transactional
    public LoanApplication resolveManualReview(UUID applicationId, boolean approve) {
        LoanApplication app = findOrThrow(applicationId);
        assertAnchorFlow(app);
        if (app.getStatus() != ApplicationStatus.UNDERWRITING
                || !"MANUAL_REVIEW".equals(app.getCreditDecision())) {
            throw new BusinessRuleException(
                    "No pending anchor manual review. Current: " + app.getStatus()
                            + " / " + app.getCreditDecision());
        }
        if (approve) {
            app.setCreditDecision("APPROVED");
            app.setStatus(ApplicationStatus.SANCTION_PENDING);
            if (app.getSanctionedAmount() == null && app.getRequestedAmount() != null) {
                app.setSanctionedAmount(app.getRequestedAmount());
            }
        } else {
            app.setCreditDecision("REJECTED");
            app.setStatus(ApplicationStatus.REJECTED);
        }
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        if (approve) {
            triggerPlpAnchorPush(applicationId);
        }
        auditService.logEvent(applicationId, "FLOW", "ANCHOR_UW_MANUAL_RESOLVED", null, null,
                Map.of("decision", app.getCreditDecision(), "status", app.getStatus().name()),
                "Anchor manual underwriting " + (approve ? "approved" : "rejected"));
        return app;
    }

    private void triggerPlpAnchorPush(UUID applicationId) {
        try {
            plpAnchorSanctionHookService.onAnchorCreditRatingCompleted(applicationId);
        } catch (Exception e) {
            // Credit decision is preserved — PLP sync can be retried from sanction refresh.
            org.slf4j.LoggerFactory.getLogger(AnchorDueDiligenceService.class)
                    .error("[PLP-ANCHOR-RATING] PLP anchor sync failed for {} — credit rating decision preserved: {}",
                            applicationId, e.getMessage(), e);
        }
    }

    private Map<String, Object> underwritingResult(LoanApplication app, Map<String, Object> block, String decision) {
        Map<String, Object> out = new LinkedHashMap<>(block);
        out.put("applicationId", app.getId());
        out.put("applicationNumber", app.getApplicationNumber());
        out.put("status", app.getStatus().name());
        out.put("decision", decision);
        out.put("creditDecision", app.getCreditDecision());
        return out;
    }

    private void assertAnchorFlow(LoanApplication app) {
        if (!InvoiceDiscountingApplicationRules.isAnchorFlow(app)) {
            throw new BusinessRuleException(
                    "Due diligence underwriting applies only to invoice discounting anchor applications",
                    "NOT_ANCHOR_FLOW",
                    "OPEN_APPLICATION",
                    null);
        }
    }

    private void assertKycReady(LoanApplication app, UUID applicationId) {
        Map<String, Object> kycOutcome = kycOrchestrationService.computeKycOutcome(applicationId);
        String outcome = String.valueOf(kycOutcome.getOrDefault("outcome", "INCOMPLETE"));
        if (!"PASS".equalsIgnoreCase(outcome)) {
            throw new BusinessRuleException(
                    "KYC must pass before anchor due diligence",
                    "KYC_OUTCOME_NOT_PASS",
                    "COMPLETE_KYC",
                    Map.of("kycOutcome", outcome));
        }
        if (app.getStatus() != ApplicationStatus.KYC_IN_PROGRESS
                && app.getStatus() != ApplicationStatus.UNDERWRITING
                && app.getStatus() != ApplicationStatus.SANCTION_PENDING) {
            // Allow re-save while in underwriting manual review
            if (!(app.getStatus() == ApplicationStatus.UNDERWRITING && "MANUAL_REVIEW".equals(app.getCreditDecision()))) {
                throw new BusinessRuleException(
                        "Due diligence is not available in status " + app.getStatus(),
                        "INVALID_STATUS",
                        "OPEN_APPLICATION",
                        Map.of("status", app.getStatus().name()));
            }
        }
    }

    private LoanApplication findOrThrow(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readDueDiligenceBlock(LoanApplication app) {
        Map<String, Object> fi = app.getFinancialInfo();
        if (fi == null) {
            return emptyBlock();
        }
        Object raw = fi.get("anchorDueDiligence");
        if (!(raw instanceof Map<?, ?> map)) {
            return emptyBlock();
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private static Map<String, Object> emptyBlock() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answers", new LinkedHashMap<>());
        m.put("creditRating", "");
        m.put("score", 0);
        return m;
    }

    private static Map<String, Object> normalizeAnswers(Map<String, Object> answers) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (answers != null) {
            for (String key : QUESTION_KEYS) {
                if (answers.containsKey(key) && answers.get(key) != null) {
                    out.put(key, String.valueOf(answers.get(key)).trim());
                }
            }
        }
        return out;
    }

    static Map<String, Object> computeRatingBlock(Map<String, Object> answers, boolean requireComplete) {
        int score = 0;
        score += scoreExternalRating(stringVal(answers, "externalCreditRating"));
        score += scoreChoice(stringVal(answers, "financialPerformance"),
                Map.of("STRONG", 15, "SATISFACTORY", 10, "WEAK", 4, "DISTRESSED", 0));
        score += scoreChoice(stringVal(answers, "businessVintage"),
                Map.of("GTE_10", 12, "Y5_9", 9, "Y3_4", 5, "LT_3", 0));
        score += scoreChoice(stringVal(answers, "gstCompliance"),
                Map.of("FULL", 12, "MINOR_DELAYS", 7, "SIGNIFICANT_GAPS", 0));
        score += scoreChoice(stringVal(answers, "industryRisk"),
                Map.of("LOW", 10, "MEDIUM", 6, "HIGH", 2));
        score += scoreChoice(stringVal(answers, "adverseNewsFlow"),
                Map.of("NONE", 12, "MINOR", 6, "MATERIAL", 0));
        score += scoreChoice(stringVal(answers, "legalLitigation"),
                Map.of("NONE", 10, "RESOLVED", 6, "ONGOING_MATERIAL", 0));
        score += scoreChoice(stringVal(answers, "managementTrackRecord"),
                Map.of("STRONG", 12, "ADEQUATE", 8, "CONCERNS", 2));

        String rating = ratingFromScore(score);
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("answers", new LinkedHashMap<>(answers));
        block.put("score", score);
        block.put("creditRating", rating);
        block.put("updatedAt", Instant.now().toString());
        if (requireComplete) {
            block.put("completedAt", Instant.now().toString());
        }
        return block;
    }

    private static String ratingFromScore(int score) {
        if (score >= 80) return "A";
        if (score >= 65) return "B";
        if (score >= 50) return "C";
        return "D";
    }

    private static int scoreExternalRating(String v) {
        return switch (v) {
            case "AAA", "AA" -> 17;
            case "A" -> 15;
            case "BBB" -> 12;
            case "BB" -> 8;
            case "B" -> 5;
            case "C", "D" -> 0;
            case "NOT_RATED" -> 6;
            default -> 0;
        };
    }

    private static int scoreChoice(String v, Map<String, Integer> table) {
        if (v.isBlank()) return 0;
        return table.getOrDefault(v, 0);
    }

    private static String stringVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v).trim().toUpperCase();
    }

    private static void mergeDueDiligence(LoanApplication app, Map<String, Object> block) {
        Map<String, Object> fi = app.getFinancialInfo() != null
                ? new LinkedHashMap<>(app.getFinancialInfo()) : new LinkedHashMap<>();
        fi.put("anchorDueDiligence", block);
        app.setFinancialInfo(fi);
    }
}
