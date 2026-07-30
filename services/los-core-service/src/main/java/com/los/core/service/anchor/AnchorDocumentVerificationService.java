package com.los.core.service.anchor;

import com.los.core.config.RabbitMQConfig;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.IntakeOwner;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.core.service.loan.InvoiceDiscountingSanctionDefaultsService;
import com.los.core.service.notification.WelcomeOnboardingNotifier;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.config.PlpProperties;
import com.los.plp.mapper.PlpAnchorPayloadMapper;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.enums.ProgramApprovalStatus;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.service.PlpProgramSyncService;
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
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnchorDocumentVerificationService {

    private final LoanApplicationRepository applicationRepository;
    private final AnchorMasterRepository anchorMasterRepository;
    private final AuditService auditService;
    private final PlpProperties plpProperties;
    private final PlpIntegrationClient plpIntegrationClient;
    private final InvoiceDiscountingSanctionDefaultsService invoiceDiscountingSanctionDefaultsService;
    private final PlpProgramSyncService plpProgramSyncService;
    private final WelcomeOnboardingNotifier welcomeOnboardingNotifier;
    private final RabbitTemplate rabbitTemplate;

    @Value("${los.anchor-portal-url:${los.sanction.notification.anchor-portal-url:http://localhost:3200/plp-anchor}}")
    private String anchorPortalUrl;

    public void notifyEsignCompletePendingVerification(LoanApplication app) {
        String email = ApplicationPartyResolver.resolveEmail(app);
        if (email.isBlank()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("anchorName", ApplicationPartyResolver.resolveDisplayName(app));
        data.put("applicationNumber", app.getApplicationNumber());
        publishEmail(email, "ANCHOR_DOC_VERIFICATION_PENDING", "ANCHOR_DOC_VERIFY", app.getId(), data);
    }

    @Transactional
    public LoanApplication approveDocumentVerification(UUID applicationId, String userRole) {
        requireOpsRole(userRole);
        LoanApplication app = findAnchor(applicationId);
        if (app.getStatus() != ApplicationStatus.DOC_VERIFICATION_PENDING
                && app.getStatus() != ApplicationStatus.DOC_VERIFICATION_SENT_BACK) {
            throw new BusinessRuleException(
                    "Document verification approve requires DOC_VERIFICATION_PENDING or DOC_VERIFICATION_SENT_BACK. Current: "
                            + app.getStatus());
        }
        ApplicationStatus from = app.getStatus();
        app.setStatus(ApplicationStatus.SANCTIONED);
        app.setDocVerificationNotes(null);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);

        activateProgramAfterVerification(app);
        markAnchorOnboardingCompleted(app);

        auditService.logEvent(applicationId, "FLOW", "DOC_VERIFICATION_APPROVED", null,
                Map.of("status", from.name()),
                Map.of("status", ApplicationStatus.SANCTIONED.name()),
                "Operations approved document verification");

        try {
            welcomeOnboardingNotifier.sendWelcomeAfterEsignComplete(app, true, false);
        } catch (Exception e) {
            log.error("[WELCOME_EMAIL] after doc verification failed: {}", e.getMessage());
        }
        return app;
    }

    @Transactional
    public LoanApplication sendBackDocumentVerification(UUID applicationId, String notes, String userRole) {
        requireOpsRole(userRole);
        LoanApplication app = findAnchor(applicationId);
        if (app.getStatus() != ApplicationStatus.DOC_VERIFICATION_PENDING) {
            throw new BusinessRuleException(
                    "Document verification send-back requires DOC_VERIFICATION_PENDING. Current: "
                            + app.getStatus());
        }
        String trimmed = notes == null ? null : notes.trim();
        app.setStatus(ApplicationStatus.DOC_VERIFICATION_SENT_BACK);
        app.setDocVerificationNotes(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        // Ensure portal can resume corrections even when staff originally filled intake.
        if (app.getIntakeOwner() != IntakeOwner.ANCHOR) {
            app.setIntakeOwner(IntakeOwner.ANCHOR);
        }
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);

        updatePlpOnboardingStatus(app, "SENT_BACK");

        auditService.logEvent(applicationId, "FLOW", "DOC_VERIFICATION_SENT_BACK", null,
                Map.of("status", ApplicationStatus.DOC_VERIFICATION_PENDING.name()),
                Map.of("status", ApplicationStatus.DOC_VERIFICATION_SENT_BACK.name()),
                "Operations sent documents back to anchor");

        String email = ApplicationPartyResolver.resolveEmail(app);
        if (!email.isBlank()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("anchorName", ApplicationPartyResolver.resolveDisplayName(app));
            data.put("applicationNumber", app.getApplicationNumber());
            data.put("notes", trimmed != null && !trimmed.isBlank() ? trimmed : "(No notes provided)");
            data.put("portalUrl", trimSlash(anchorPortalUrl) + "/onboarding/continue");
            publishEmail(email, "ANCHOR_DOC_VERIFICATION_SENT_BACK", "ANCHOR_DOC_SEND_BACK", app.getId(), data);
        }
        return app;
    }

    private void activateProgramAfterVerification(LoanApplication app) {
        if (!plpProperties.isEnabled()) {
            return;
        }
        try {
            ProgramMaster program = invoiceDiscountingSanctionDefaultsService.resolveAnchorProgram(app)
                    .orElse(null);
            if (program == null || program.getId() == null) {
                log.warn("No program to activate after doc verification for {}", app.getId());
                return;
            }
            if (program.getApprovalStatus() == ProgramApprovalStatus.APPROVED) {
                return;
            }
            plpProgramSyncService.activate(program);
        } catch (Exception e) {
            log.error("Failed to activate PLP program after doc verification for {}: {}",
                    app.getId(), e.getMessage(), e);
            throw new BusinessRuleException(
                    "Document verification approved but program activation failed: " + e.getMessage());
        }
    }

    private void markAnchorOnboardingCompleted(LoanApplication app) {
        updatePlpOnboardingStatus(app, "COMPLETED");
    }

    private void updatePlpOnboardingStatus(LoanApplication app, String onboardingStatus) {
        if (!plpProperties.isEnabled()) {
            return;
        }
        try {
            AnchorMaster anchor = anchorMasterRepository.findBySourceAnchorApplicationId(app.getId())
                    .orElse(null);
            if (anchor == null) {
                return;
            }
            var request = PlpAnchorPayloadMapper.toRequest(anchor);
            request.setOnboardingStatus(onboardingStatus);
            plpIntegrationClient.syncAnchor(request);
        } catch (Exception e) {
            log.warn("Failed to set PLP onboardingStatus={} for {}: {}",
                    onboardingStatus, app.getId(), e.getMessage());
        }
    }

    private LoanApplication findAnchor(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (!InvoiceDiscountingApplicationRules.isAnchorFlow(app)) {
            throw new BusinessRuleException("Document verification is only for anchor applications");
        }
        return app;
    }

    private static void requireOpsRole(String userRole) {
        String role = userRole == null ? "" : userRole.trim().toUpperCase();
        if (role.isEmpty()) {
            return;
        }
        if (role.contains("OPERATIONS") || role.contains("ADMIN") || role.contains("PLATFORM")) {
            return;
        }
        throw new BusinessRuleException("Only Operations or Admin can perform document verification");
    }

    private void publishEmail(
            String recipient, String templateCode, String eventType, UUID applicationId, Map<String, Object> data) {
        RoutingEmailEvent event = RoutingEmailEvent.builder()
                .channel("EMAIL")
                .recipient(recipient.trim())
                .templateCode(templateCode)
                .eventType(eventType)
                .applicationId(applicationId)
                .templateData(data)
                .build();
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE, "notification.email.anchor_intake", event);
        } catch (Exception e) {
            log.error("{} email failed for {}: {}", templateCode, applicationId, e.getMessage());
        }
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
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
