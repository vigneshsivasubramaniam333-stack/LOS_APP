package com.los.core.service.flow.step;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.SanctionRecordRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.config.EsignNotificationProperties;
import com.los.core.service.esign.EsignDocumentsConfig;
import com.los.core.service.esign.EsignSigningLinkNotifier;
import com.los.core.service.esign.EsignRequestTrackingService;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.ApplicationPartyService;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.los.core.model.entity.ApplicationParty;
import com.los.core.model.entity.Document;
import com.los.core.model.enums.ApplicationPartyRole;
import com.los.core.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link FlowStepType#ESIGN} — eSign initiation via {@link IIntegrationRouterService}. Moved from
 * {@code LoanApplicationFlowService#initiateESign}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsignInitiateStepExecutor implements IStepExecutor {

    private final LoanApplicationRepository applicationRepository;
    private final KfsDocumentRepository kfsDocumentRepository;
    private final SanctionRecordRepository sanctionRecordRepository;
    private final KfsService kfsService;
    private final IIntegrationRouterService integrationRouter;
    private final AuditService auditService;
    private final EsignRequestTrackingService esignRequestTrackingService;
    private final EsignSigningLinkNotifier esignSigningLinkNotifier;
    private final EsignNotificationProperties esignNotificationProperties;
    private final ApplicationPartyService applicationPartyService;
    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final DocumentRepository documentRepository;

    @Override
    public boolean supports(String stepType) {
        return FlowStepType.ESIGN.equals(stepType);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public StepResult execute(UUID applicationId, Map<String, Object> context) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.los.core.exception.ResourceNotFoundException("Application not found: " + applicationId));
        app = ensureInvoiceDiscountingBorrowerTermsReady(applicationId, app);
        if (!esignAllowedForStatus(app)) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "ESIGN_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "KFS_NOT_READY", "action", "ESIGN_INITIATE"),
                    null,
                    "eSign blocked: requires KFS_GENERATED, SANCTION_ISSUED, anchor ESIGN_PENDING, or invoice discounting borrower SANCTIONED");
            throw new BusinessRuleException(
                    "Cannot initiate eSign — application must be in KFS_GENERATED, SANCTION_ISSUED, "
                            + "anchor ESIGN_PENDING (program terms), or invoice discounting borrower SANCTIONED. Current: "
                            + app.getStatus(),
                    "ESIGN_STATUS_INVALID",
                    "ESIGN_INITIATE",
                    Map.of("status", app.getStatus().name())
            );
        }
        Map<String, Object> signerInfo = (context != null && context.get("signerInfo") != null)
                ? (Map<String, Object>) context.get("signerInfo")
                : Map.of();
        applicationPartyService.ensurePrimaryParty(app);
        boolean multiParty = applicationPartyService.isMultiPartyEnabled(app)
                && applicationPartyService.partiesRequiredForDisbursement(applicationId).size() > 1;

        if (multiParty) {
            return initiateForAllRequiredParties(applicationId, app, signerInfo);
        }

        Map<String, Object> signerForRoute = ApplicationPartyResolver.enrichEsignSignerInfo(app, signerInfo);
        EsignDocumentsConfig.Settings docs = resolveEsignDocuments(app);
        assertRequiredAdditionalDocuments(applicationId, docs);
        if (docs.additional().isEmpty()) {
            return initiateForSingleSigner(applicationId, app, signerForRoute, null, docs.defaultDocumentKey());
        }
        return initiateMultiDocument(applicationId, app, signerForRoute, docs);
    }

    private StepResult initiateMultiDocument(
            UUID applicationId,
            LoanApplication app,
            Map<String, Object> signerForRoute,
            EsignDocumentsConfig.Settings docs) {
        List<Map<String, Object>> docResults = new ArrayList<>();
        boolean anyFail = false;
        String primaryTxn = null;
        String primaryUrl = "";

        List<String> keys = new ArrayList<>();
        keys.add(docs.defaultDocumentKey());
        for (EsignDocumentsConfig.AdditionalDoc d : docs.additional()) {
            if (d.required()) {
                keys.add(d.documentType());
            }
        }
        for (String documentKey : keys) {
            StepResult one = initiateForSingleSigner(applicationId, app, signerForRoute, null, documentKey);
            Map<String, Object> out = one.output() != null ? new HashMap<>(one.output()) : new HashMap<>();
            out.put("documentKey", documentKey);
            docResults.add(out);
            boolean ok = Boolean.TRUE.equals(out.get("esignSuccess"))
                    || "true".equalsIgnoreCase(String.valueOf(out.get("esignSuccess")));
            if (!ok) {
                anyFail = true;
            } else if (primaryTxn == null || primaryTxn.isBlank()) {
                primaryTxn = String.valueOf(out.getOrDefault("transactionId", ""));
                primaryUrl = String.valueOf(out.getOrDefault("signingUrl", ""));
            }
        }
        app = applicationRepository.findById(applicationId).orElse(app);
        Map<String, Object> aggregate = new HashMap<>();
        aggregate.put("applicationId", applicationId);
        aggregate.put("applicationNumber", app.getApplicationNumber());
        aggregate.put("status", app.getStatus().name());
        aggregate.put("esignSuccess", !anyFail);
        aggregate.put("transactionId", primaryTxn != null ? primaryTxn : "");
        aggregate.put("signingUrl", primaryUrl);
        aggregate.put("errorMessage", anyFail ? "One or more document eSign initiations failed" : "");
        aggregate.put("documentResults", docResults);
        aggregate.put("multiDocument", true);
        return anyFail ? StepResult.fail(aggregate) : StepResult.ok(aggregate);
    }

    private EsignDocumentsConfig.Settings resolveEsignDocuments(LoanApplication app) {
        try {
            return activeWorkflowConfigService.findActiveForApplication(app)
                    .map(wf -> EsignDocumentsConfig.fromWorkflowSteps(wf.getSteps()))
                    .orElseGet(EsignDocumentsConfig.Settings::singleDefault);
        } catch (Exception e) {
            log.warn("Could not resolve esignDocuments from workflow for {}: {}", app.getId(), e.getMessage());
            return EsignDocumentsConfig.Settings.singleDefault();
        }
    }

    private void assertRequiredAdditionalDocuments(UUID applicationId, EsignDocumentsConfig.Settings docs) {
        List<String> missing = new ArrayList<>();
        for (String type : docs.requiredAdditionalTypes()) {
            List<Document> found = documentRepository
                    .findByApplicationIdAndDocumentTypeOrderByVersionNumberDesc(applicationId, type);
            if (found == null || found.isEmpty()) {
                missing.add(type);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(
                    "Cannot initiate eSign — missing required documents for signing: " + String.join(", ", missing),
                    "ESIGN_DOCUMENTS_MISSING",
                    "ESIGN_INITIATE",
                    Map.of("missingDocumentTypes", missing));
        }
    }

    private StepResult initiateForAllRequiredParties(
            UUID applicationId, LoanApplication app, Map<String, Object> baseSignerInfo) {
        List<ApplicationParty> parties = applicationPartyService.partiesRequiredForDisbursement(applicationId);
        List<Map<String, Object>> partyResults = new ArrayList<>();
        boolean anyFail = false;
        String primaryTxn = null;
        String primaryUrl = "";

        for (ApplicationParty party : parties) {
            Map<String, Object> signerForRoute = ApplicationPartyResolver.enrichEsignSignerInfoFromParty(
                    party, baseSignerInfo);
            StepResult one = initiateForSingleSigner(applicationId, app, signerForRoute, party, "KFS_AGREEMENT");
            Map<String, Object> out = one.output() != null ? new HashMap<>(one.output()) : new HashMap<>();
            out.put("partyId", party.getId().toString());
            out.put("partyRole", party.getRole().name());
            partyResults.add(out);
            boolean ok = Boolean.TRUE.equals(out.get("esignSuccess"))
                    || "true".equalsIgnoreCase(String.valueOf(out.get("esignSuccess")));
            if (!ok) {
                anyFail = true;
            } else if (party.getRole() == ApplicationPartyRole.PRIMARY) {
                primaryTxn = String.valueOf(out.getOrDefault("transactionId", ""));
                primaryUrl = String.valueOf(out.getOrDefault("signingUrl", ""));
            }
        }

        app = applicationRepository.findById(applicationId).orElse(app);
        Map<String, Object> aggregate = new HashMap<>();
        aggregate.put("applicationId", applicationId);
        aggregate.put("applicationNumber", app.getApplicationNumber());
        aggregate.put("status", app.getStatus().name());
        aggregate.put("esignSuccess", !anyFail);
        aggregate.put("transactionId", primaryTxn != null ? primaryTxn : "");
        aggregate.put("signingUrl", primaryUrl);
        aggregate.put("errorMessage", anyFail ? "One or more party eSign initiations failed" : "");
        aggregate.put("partyResults", partyResults);
        aggregate.put("multiParty", true);
        return anyFail ? StepResult.fail(aggregate) : StepResult.ok(aggregate);
    }

    private StepResult initiateForSingleSigner(
            UUID applicationId,
            LoanApplication app,
            Map<String, Object> signerForRoute,
            ApplicationParty party,
            String documentKey) {
        Object email = signerForRoute.get("email");
        Object borrowerEmail = signerForRoute.get("borrowerEmail");
        if ((borrowerEmail == null || borrowerEmail.toString().isBlank())
                && email != null
                && !email.toString().isBlank()) {
            signerForRoute.put("borrowerEmail", email.toString().trim());
        }

        String docKey = documentKey != null && !documentKey.isBlank() ? documentKey : "KFS_AGREEMENT";
        Map<String, Object> esignPayload = new HashMap<>();
        esignPayload.put("documentKey", docKey);
        esignPayload.put("signerInfo", signerForRoute);
        esignPayload.put("esignStepType", "ESIGN_AGREEMENT");
        if (party != null && party.getId() != null) {
            esignPayload.put("partyId", party.getId().toString());
        }
        IIntegrationRouterService.ESignRouteResult result = integrationRouter.routeESignRequest(applicationId, esignPayload);

        Map<String, Object> meta = result.providerMetadata();
        boolean reusedSigningUrl = meta != null && Boolean.TRUE.equals(meta.get("reusedSigningUrl"));

        if (result.success()) {
            app.setStatus(ApplicationStatus.ESIGN_PENDING);
            if (party == null || party.getRole() == ApplicationPartyRole.PRIMARY) {
                if ("KFS_AGREEMENT".equalsIgnoreCase(docKey)
                        || "ANCHOR_PROGRAM_TERMS".equalsIgnoreCase(docKey)
                        || app.getEsignTransactionId() == null) {
                    app.setEsignTransactionId(result.transactionId());
                }
            }
            app.setCurrentStepStartedAt(Instant.now());
            app = applicationRepository.save(app);

            auditService.logEvent(applicationId, "FLOW", "ESIGN_INITIATED",
                    null, Map.of("status", app.getStatus().name()),
                    Map.of("status", "ESIGN_PENDING",
                            "esignTransactionId", result.transactionId(),
                            "documentKey", docKey,
                            "partyId", party != null && party.getId() != null ? party.getId().toString() : ""),
                    "eSign initiated");

            log.info("eSign initiated for {} — txnId: {}, documentKey: {}, signingUrl: {}, reusedSigningUrl: {}, partyId: {}",
                    app.getApplicationNumber(), result.transactionId(), docKey, result.signingUrl(), reusedSigningUrl,
                    party != null ? party.getId() : null);
            if (!reusedSigningUrl) {
                try {
                    esignRequestTrackingService.recordInitiationSuccess(
                            applicationId,
                            docKey,
                            result.providerName(),
                            result.transactionId(),
                            result.signingUrl(),
                            signerForRoute,
                            meta,
                            "ESIGN_AGREEMENT",
                            party != null ? party.getId() : null);
                } catch (Exception ex) {
                    log.warn("Failed to persist esign_requests for application {}: {}", applicationId, ex.getMessage());
                }
            } else {
                log.info("[ESIGN_PERSIST] skipped persistence (reused existing esign_requests URL) appId={} workflowId={}",
                        applicationId, result.transactionId());
            }

            if (party != null) {
                applicationPartyService.markPartyEsignPending(party);
            }
            dispatchSigningLinkEmail(applicationId, app, signerForRoute, result, reusedSigningUrl);
        } else {
            log.error("eSign initiation failed for {}: {}", app.getApplicationNumber(), result.errorMessage());
        }

        Map<String, Object> out = Map.of(
                "applicationId", applicationId,
                "applicationNumber", app.getApplicationNumber(),
                "status", app.getStatus().name(),
                "esignSuccess", result.success(),
                "transactionId", result.transactionId() != null ? result.transactionId() : "",
                "signingUrl", result.signingUrl() != null ? result.signingUrl() : "",
                "documentKey", docKey,
                "errorMessage", result.errorMessage() != null ? result.errorMessage() : ""
        );
        return StepResult.ok(out);
    }

    private LoanApplication ensureInvoiceDiscountingBorrowerTermsReady(UUID applicationId, LoanApplication app) {
        if (!InvoiceDiscountingApplicationRules.isBorrowerFlow(app)
                || app.getStatus() != ApplicationStatus.SANCTIONED) {
            return app;
        }
        if (kfsDocumentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).isPresent()) {
            app.setStatus(ApplicationStatus.KFS_GENERATED);
            app.setCurrentStepStartedAt(Instant.now());
            return applicationRepository.save(app);
        }
        SanctionRecord rec = sanctionRecordRepository.findTopByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Cannot initiate eSign — sanction record missing for invoice discounting borrower",
                        "SANCTION_RECORD_MISSING",
                        "ESIGN_INITIATE",
                        Map.of("status", ApplicationStatus.SANCTIONED.name())));
        kfsService.generateInvoiceDiscountingBorrowerTermsDocument(applicationId, rec, Map.of());
        app.setStatus(ApplicationStatus.KFS_GENERATED);
        app.setCurrentStepStartedAt(Instant.now());
        LoanApplication saved = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "SANCTION_TERMS_BACKFILL",
                null, Map.of("status", "SANCTIONED"),
                Map.of("status", "KFS_GENERATED", "documentKind", "INVOICE_DISCOUNTING_TERMS"),
                "Legacy invoice discounting borrower — terms document backfilled for eSign");
        log.info("Backfilled invoice discounting terms document for {} — status KFS_GENERATED",
                saved.getApplicationNumber());
        return saved;
    }

    private static boolean esignAllowedForStatus(LoanApplication app) {
        if (app.getStatus() == ApplicationStatus.ESIGN_PENDING
                && InvoiceDiscountingApplicationRules.isAnchorFlow(app)) {
            return true;
        }
        if (app.getStatus() == ApplicationStatus.SANCTION_ISSUED
                || app.getStatus() == ApplicationStatus.KFS_GENERATED) {
            return true;
        }
        return app.getStatus() == ApplicationStatus.SANCTIONED
                && InvoiceDiscountingApplicationRules.isBorrowerFlow(app);
    }

    private void dispatchSigningLinkEmail(
            UUID applicationId,
            LoanApplication app,
            Map<String, Object> signerForRoute,
            IIntegrationRouterService.ESignRouteResult result,
            boolean reusedSigningUrl) {
        if (!esignNotificationProperties.isEnabled()) {
            log.info("[ESIGN_EMAIL] skipped (los.esign.notification.enabled=false) applicationId={}", applicationId);
            return;
        }
        if (reusedSigningUrl) {
            log.info("[ESIGN_EMAIL] skipped (reusedSigningUrl=true) applicationId={}", applicationId);
            return;
        }
        String rawEmail = borrowerEmailRaw(signerForRoute);
        if (rawEmail == null || rawEmail.isBlank()) {
            log.warn("[ESIGN_EMAIL] skipped (no borrower email on signer info) applicationId={}", applicationId);
            return;
        }
        String name = signerForRoute.get("name") != null ? signerForRoute.get("name").toString().trim() : "Borrower";
        try {
            esignSigningLinkNotifier.publishSigningLinkEmail(
                    applicationId,
                    app.getApplicationNumber(),
                    name,
                    List.of(rawEmail.trim()),
                    result.signingUrl() != null ? result.signingUrl() : "",
                    esignNotificationProperties.getTemplateCode(),
                    esignNotificationProperties.getLinkExpiryHours(),
                    false);
        } catch (Exception ex) {
            log.error("[ESIGN_EMAIL] unexpected failure applicationId={}: {}", applicationId, ex.getMessage(), ex);
        }
    }

    private static String borrowerEmailRaw(Map<String, Object> signerInfo) {
        if (signerInfo == null) {
            return null;
        }
        Object v = firstNonNullObj(
                signerInfo.get("borrowerEmail"),
                signerInfo.get("email"),
                signerInfo.get("signerEmail"),
                signerInfo.get("EmailId"));
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Object firstNonNullObj(Object... o) {
        for (Object x : o) {
            if (x != null) {
                return x;
            }
        }
        return null;
    }
}
