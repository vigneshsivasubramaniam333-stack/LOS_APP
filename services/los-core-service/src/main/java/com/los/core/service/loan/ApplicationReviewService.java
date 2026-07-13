package com.los.core.service.loan;

import com.los.core.config.RabbitMQConfig;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.IntakeOwner;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationReviewService {

    private final LoanApplicationRepository applicationRepository;
    private final ILoanApplicationService loanApplicationService;
    private final AuditService auditService;
    private final RabbitTemplate rabbitTemplate;
    private final WorkflowRoleGuard workflowRoleGuard;

    @Value("${los.borrower-ui-url:http://localhost:5173/borrower}")
    private String borrowerUiUrl;

    @Transactional
    public ApplicationResponse acceptForProcessing(UUID applicationId, String userRole) {
        LoanApplication app = requireApp(applicationId);
        ApplicationStatus status = app.getStatus();
        String role = normalizeRole(userRole);

        if (status == ApplicationStatus.PENDING_CREDIT_OFFICER) {
            workflowRoleGuard.requireCreditOfficerOrAdmin(userRole);
        } else if (status == ApplicationStatus.BORROWER_SUBMITTED) {
            // Admin break-glass: accept without RM handoff
            if (!isAdmin(role)) {
                throw new BusinessRuleException(
                        "Accept from BORROWER_SUBMITTED requires Admin. "
                                + "Credit Officer must wait for RM handoff (PENDING_CREDIT_OFFICER). Current: "
                                + status);
            }
        } else {
            throw new BusinessRuleException(
                    "Accept is only for PENDING_CREDIT_OFFICER (or Admin from BORROWER_SUBMITTED). Current: "
                            + status);
        }

        ApplicationStatus from = status;
        app.setStatus(ApplicationStatus.KYC_IN_PROGRESS);
        app.setIntakeOwner(IntakeOwner.STAFF);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "BORROWER_REVIEW_ACCEPTED", null,
                Map.of("status", from.name()),
                Map.of("status", ApplicationStatus.KYC_IN_PROGRESS.name()),
                "Accepted application for KYC processing");
        return loanApplicationService.getApplication(applicationId);
    }

    @Transactional
    public ApplicationResponse sendBackToBorrower(UUID applicationId, String notes, String userRole) {
        workflowRoleGuard.requireRelationshipManagerOrAdmin(userRole);
        LoanApplication app = requireApp(applicationId);
        if (app.getStatus() != ApplicationStatus.BORROWER_SUBMITTED) {
            throw new BusinessRuleException(
                    "Send back to borrower is only for BORROWER_SUBMITTED applications. Current: "
                            + app.getStatus());
        }
        String trimmed = trimToNull(notes);
        app.setStatus(ApplicationStatus.BORROWER_SENT_BACK);
        app.setBorrowerSentBackNotes(trimmed);
        app.setIntakeOwner(IntakeOwner.BORROWER);
        storeReviewNotes(app, "SENT_BACK_TO_BORROWER", trimmed);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "BORROWER_SENT_BACK", null,
                Map.of("status", ApplicationStatus.BORROWER_SUBMITTED.name()),
                Map.of("status", ApplicationStatus.BORROWER_SENT_BACK.name(),
                        "notes", trimmed != null ? trimmed : ""),
                "Sent application back to borrower for more details");
        publishSendBackEmail(app, trimmed != null ? trimmed : "");
        return loanApplicationService.getApplication(applicationId);
    }

    @Transactional
    public ApplicationResponse handOffToCreditOfficer(UUID applicationId, String notes, String userRole) {
        workflowRoleGuard.requireRelationshipManagerOrAdmin(userRole);
        LoanApplication app = requireApp(applicationId);
        ApplicationStatus status = app.getStatus();
        if (status != ApplicationStatus.BORROWER_SUBMITTED
                && status != ApplicationStatus.SENT_BACK_TO_RM) {
            throw new BusinessRuleException(
                    "Hand off to Credit Officer is only from BORROWER_SUBMITTED or SENT_BACK_TO_RM. Current: "
                            + status);
        }
        String trimmed = trimToNull(notes);
        ApplicationStatus from = status;
        app.setStatus(ApplicationStatus.PENDING_CREDIT_OFFICER);
        app.setIntakeOwner(IntakeOwner.STAFF);
        storeReviewNotes(app, "HANDOFF_TO_CREDIT_OFFICER", trimmed);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "HANDOFF_TO_CREDIT_OFFICER", null,
                Map.of("status", from.name()),
                Map.of("status", ApplicationStatus.PENDING_CREDIT_OFFICER.name(),
                        "notes", trimmed != null ? trimmed : ""),
                "RM handed application to Credit Officer");
        return loanApplicationService.getApplication(applicationId);
    }

    @Transactional
    public ApplicationResponse sendBackToRelationshipManager(
            UUID applicationId, String notes, String userRole) {
        workflowRoleGuard.requireCreditOfficerOrAdmin(userRole);
        LoanApplication app = requireApp(applicationId);
        if (app.getStatus() != ApplicationStatus.PENDING_CREDIT_OFFICER) {
            throw new BusinessRuleException(
                    "Send back to RM is only for PENDING_CREDIT_OFFICER applications. Current: "
                            + app.getStatus());
        }
        String trimmed = trimToNull(notes);
        app.setStatus(ApplicationStatus.SENT_BACK_TO_RM);
        app.setIntakeOwner(IntakeOwner.STAFF);
        storeReviewNotes(app, "SENT_BACK_TO_RM", trimmed);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "SENT_BACK_TO_RM", null,
                Map.of("status", ApplicationStatus.PENDING_CREDIT_OFFICER.name()),
                Map.of("status", ApplicationStatus.SENT_BACK_TO_RM.name(),
                        "notes", trimmed != null ? trimmed : ""),
                "Credit Officer sent application back to Relationship Manager");
        return loanApplicationService.getApplication(applicationId);
    }

    private LoanApplication requireApp(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
    }

    private static void storeReviewNotes(LoanApplication app, String action, String notes) {
        Map<String, Object> fi = app.getFinancialInfo() != null
                ? new LinkedHashMap<>(app.getFinancialInfo())
                : new LinkedHashMap<>();
        Map<String, Object> reviewNotes = new LinkedHashMap<>();
        reviewNotes.put("lastAction", action);
        if (notes != null && !notes.isBlank()) {
            reviewNotes.put("notes", notes);
        }
        reviewNotes.put("at", Instant.now().toString());
        fi.put("reviewNotes", reviewNotes);
        app.setFinancialInfo(fi);
    }

    private static String trimToNull(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        return notes.trim();
    }

    private static String normalizeRole(String userRole) {
        return userRole == null ? "" : userRole.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isAdmin(String role) {
        return "ADMIN".equals(role) || "ADMINISTRATOR".equals(role);
    }

    private void publishSendBackEmail(LoanApplication app, String notes) {
        String email = ApplicationPartyResolver.resolveEmail(app);
        if (email.isBlank()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("borrowerName", ApplicationPartyResolver.resolveDisplayName(app));
        data.put("applicationNumber", app.getApplicationNumber());
        data.put("notes", notes);
        data.put("portalUrl", borrowerUiUrl + "/apply?resume=" + app.getId());

        RoutingEmailEvent event = RoutingEmailEvent.builder()
                .channel("EMAIL")
                .recipient(email.trim())
                .templateCode("BORROWER_APPLICATION_SENT_BACK")
                .eventType("BORROWER_SENT_BACK")
                .applicationId(app.getId())
                .templateData(data)
                .build();
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE, "notification.email.borrower_sent_back", event);
        } catch (Exception e) {
            log.error("Send-back email failed for {}: {}", app.getId(), e.getMessage());
        }
    }

    @Data
    @Builder
    private static final class RoutingEmailEvent {
        private String channel;
        private String recipient;
        private String templateCode;
        private String eventType;
        private Map<String, Object> templateData;
        private UUID applicationId;
    }
}
