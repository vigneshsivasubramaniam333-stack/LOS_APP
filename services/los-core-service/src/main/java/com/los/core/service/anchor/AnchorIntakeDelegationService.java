package com.los.core.service.anchor;

import com.los.core.config.RabbitMQConfig;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.IntakeOwner;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.config.PlpProperties;
import com.los.plp.dto.PlpApiResponse;
import com.los.plp.dto.response.PlpAnchorSyncData;
import com.los.plp.mapper.PlpAnchorPayloadMapper;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.service.PlpAnchorSyncService;
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

/**
 * Delegated Anchor intake: RM notifies with basics; anchor completes in PLP portal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnchorIntakeDelegationService {

    private final LoanApplicationRepository applicationRepository;
    private final AnchorMasterRepository anchorMasterRepository;
    private final PlpAnchorSyncService plpAnchorSyncService;
    private final PlpIntegrationClient plpIntegrationClient;
    private final PlpProperties plpProperties;
    private final AuditService auditService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${los.anchor-portal-url:${los.sanction.notification.anchor-portal-url:http://localhost:3200/plp-anchor}}")
    private String anchorPortalUrl;

    @Transactional
    public LoanApplication saveDraftAndNotifyAnchor(UUID applicationId, int completedStep) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        if (app.getIntakeSegment() != IntakeSegment.ANCHOR) {
            throw new BusinessRuleException("Notify anchor is only for ANCHOR applications");
        }
        if (!StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING.equals(app.getLoanProduct())) {
            throw new BusinessRuleException("Notify anchor requires invoice-discounting product");
        }
        if (app.getStatus() != ApplicationStatus.DRAFT
                && app.getStatus() != ApplicationStatus.ANCHOR_CONSENT_PENDING) {
            throw new BusinessRuleException(
                    "Notify anchor is only allowed from DRAFT or ANCHOR_CONSENT_PENDING. Current: "
                            + app.getStatus());
        }

        String name = ApplicationPartyResolver.resolveDisplayName(app);
        String email = ApplicationPartyResolver.resolveEmail(app);
        String mobile = ApplicationPartyResolver.resolveMobile(app);
        if (name.isBlank()) {
            throw new BusinessRuleException("Anchor name is required before notify");
        }
        if (email.isBlank()) {
            throw new BusinessRuleException("Anchor email is required before notify");
        }
        if (mobile.isBlank()) {
            throw new BusinessRuleException("Anchor mobile is required before notify");
        }

        String tempPassword = provisionAnchorAtNotify(app);

        app.setStatus(ApplicationStatus.ANCHOR_CONSENT_PENDING);
        app.setIntakeOwner(IntakeOwner.ANCHOR);
        app.setIntakeCompletedStep(completedStep);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);

        auditService.logEvent(app.getId(), "FLOW", "ANCHOR_INTAKE_DELEGATED", null,
                Map.of("status", ApplicationStatus.DRAFT.name()),
                Map.of("status", ApplicationStatus.ANCHOR_CONSENT_PENDING.name(),
                        "completedStep", completedStep),
                "Staff saved draft and invited anchor to complete intake");

        publishInviteEmail(app, email, name,
                tempPassword != null && !tempPassword.isBlank()
                        ? tempPassword
                        : "Use your existing password or forgot-password to reset");
        return app;
    }

    @Transactional
    public LoanApplication submitAnchorIntake(UUID applicationId) {
        LoanApplication app = requireDelegatedAnchor(applicationId);
        if (app.getStatus() != ApplicationStatus.ANCHOR_CONSENT_PENDING
                && app.getStatus() != ApplicationStatus.ANCHOR_SENT_BACK) {
            throw new BusinessRuleException(
                    "Anchor submit requires ANCHOR_CONSENT_PENDING or ANCHOR_SENT_BACK. Current: "
                            + app.getStatus());
        }
        ApplicationStatus from = app.getStatus();
        app.setStatus(ApplicationStatus.ANCHOR_SUBMITTED);
        app.setAnchorSentBackNotes(null);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "ANCHOR_INTAKE_SUBMITTED", null,
                Map.of("status", from.name()),
                Map.of("status", ApplicationStatus.ANCHOR_SUBMITTED.name()),
                "Anchor submitted intake from portal");
        updatePlpOnboardingStatus(app, "SUBMITTED");
        return app;
    }

    @Transactional
    public LoanApplication resubmitAnchorIntake(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (!InvoiceDiscountingApplicationRules.isAnchorFlow(app)) {
            throw new BusinessRuleException("Not an anchor invoice-discounting application");
        }
        if (app.getStatus() != ApplicationStatus.ANCHOR_SENT_BACK
                && app.getStatus() != ApplicationStatus.DOC_VERIFICATION_SENT_BACK) {
            throw new BusinessRuleException(
                    "Anchor resubmit requires ANCHOR_SENT_BACK or DOC_VERIFICATION_SENT_BACK. Current: "
                            + app.getStatus());
        }
        ApplicationStatus from = app.getStatus();
        if (from == ApplicationStatus.DOC_VERIFICATION_SENT_BACK) {
            // Ops may send docs back even when staff originally filled intake (intakeOwner=STAFF).
            // Portal resubmit must still be allowed; hand ownership to the anchor portal for corrections.
            if (app.getIntakeOwner() != IntakeOwner.ANCHOR) {
                app.setIntakeOwner(IntakeOwner.ANCHOR);
            }
            app.setStatus(ApplicationStatus.DOC_VERIFICATION_PENDING);
            app.setDocVerificationNotes(null);
            updatePlpOnboardingStatus(app, "SUBMITTED");
        } else {
            if (app.getIntakeOwner() != IntakeOwner.ANCHOR) {
                throw new BusinessRuleException("Application was not delegated to the anchor portal");
            }
            app.setStatus(ApplicationStatus.ANCHOR_SUBMITTED);
            app.setAnchorSentBackNotes(null);
            updatePlpOnboardingStatus(app, "SUBMITTED");
        }
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "ANCHOR_INTAKE_RESUBMITTED", null,
                Map.of("status", from.name()),
                Map.of("status", app.getStatus().name()),
                "Anchor resubmitted after send-back");
        publishResubmitAck(app);
        return app;
    }

    @Transactional
    public LoanApplication sendBackToAnchor(UUID applicationId, String notes) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (app.getIntakeSegment() != IntakeSegment.ANCHOR) {
            throw new BusinessRuleException("Send back to anchor is only for ANCHOR applications");
        }
        if (app.getIntakeOwner() != IntakeOwner.ANCHOR) {
            throw new BusinessRuleException(
                    "Send back to anchor is only for delegated (notify) anchor applications");
        }
        ApplicationStatus from = app.getStatus();
        String trimmed = notes == null ? null : notes.trim();
        app.setStatus(ApplicationStatus.ANCHOR_SENT_BACK);
        app.setAnchorSentBackNotes(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "ANCHOR_SENT_BACK", null,
                Map.of("status", from.name()),
                Map.of("status", ApplicationStatus.ANCHOR_SENT_BACK.name()),
                "Staff sent application back to anchor");
        updatePlpOnboardingStatus(app, "SENT_BACK");
        publishSendBackEmail(app, trimmed);
        return app;
    }

    private LoanApplication requireDelegatedAnchor(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (!InvoiceDiscountingApplicationRules.isAnchorFlow(app)) {
            throw new BusinessRuleException("Not an anchor invoice-discounting application");
        }
        if (app.getIntakeOwner() != IntakeOwner.ANCHOR) {
            throw new BusinessRuleException("Application was not delegated to the anchor portal");
        }
        return app;
    }

    private String provisionAnchorAtNotify(LoanApplication app) {
        if (!plpProperties.isEnabled()) {
            log.warn("PLP disabled — skipping early anchor provision for {}", app.getId());
            return null;
        }
        AnchorMaster anchor = anchorMasterRepository.findBySourceAnchorApplicationId(app.getId())
                .orElseGet(() -> createAnchorFromApplication(app));
        refreshBasics(anchor, app);
        anchor = anchorMasterRepository.save(anchor);

        try {
            var request = PlpAnchorPayloadMapper.toRequest(anchor);
            request.setProvisionAtNotify(true);
            request.setOnboardingStatus("INVITED");
            if (app.getId() != null) {
                request.setLosApplicationId(app.getId().toString());
            }
            PlpApiResponse<PlpAnchorSyncData> response = plpIntegrationClient.syncAnchor(request);
            PlpAnchorSyncData data = response.getData();
            if (data != null) {
                if (data.getPlpAnchorId() != null) {
                    try {
                        anchor.setPlpAnchorId(UUID.fromString(data.getPlpAnchorId()));
                    } catch (IllegalArgumentException ignored) {
                        // leave unset
                    }
                }
                anchor.setPlpAnchorSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
                anchor.setPlpAnchorSyncError(null);
                anchorMasterRepository.save(anchor);
                return data.getTemporaryPassword();
            }
        } catch (Exception e) {
            log.error("Early PLP anchor provision failed for {}: {}", app.getId(), e.getMessage(), e);
            // Fallback sync path without password capture
            try {
                plpAnchorSyncService.sync(anchor.getId());
            } catch (Exception syncEx) {
                log.error("Fallback PLP sync also failed: {}", syncEx.getMessage());
            }
        }
        return null;
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
            log.warn("Failed to update PLP onboardingStatus={} for {}: {}",
                    onboardingStatus, app.getId(), e.getMessage());
        }
    }

    private AnchorMaster createAnchorFromApplication(LoanApplication app) {
        String name = ApplicationPartyResolver.resolveDisplayName(app);
        if (name.isBlank()) {
            name = "Anchor";
        }
        String code = app.getApplicationNumber();
        if (code == null || code.isBlank()) {
            code = "ANCHOR-" + app.getId().toString().substring(0, 8).toUpperCase();
        }
        return AnchorMaster.builder()
                .code(code)
                .name(name)
                .email(nullIfBlank(ApplicationPartyResolver.resolveEmail(app)))
                .mobile(nullIfBlank(ApplicationPartyResolver.resolveMobile(app)))
                .sourceAnchorApplicationId(app.getId())
                .plpAnchorSyncStatus(PlpSyncStatus.NOT_SYNCED)
                .build();
    }

    private void refreshBasics(AnchorMaster anchor, LoanApplication app) {
        String name = ApplicationPartyResolver.resolveDisplayName(app);
        if (!name.isBlank()) {
            anchor.setName(name);
        }
        String email = ApplicationPartyResolver.resolveEmail(app);
        if (!email.isBlank()) {
            anchor.setEmail(email);
        }
        String mobile = ApplicationPartyResolver.resolveMobile(app);
        if (!mobile.isBlank()) {
            anchor.setMobile(mobile);
        }
    }

    private void publishInviteEmail(LoanApplication app, String email, String name, String tempPassword) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("anchorName", name);
        data.put("applicationNumber", app.getApplicationNumber());
        data.put("portalUrl", trimSlash(anchorPortalUrl) + "/onboarding");
        data.put("loginEmail", email);
        data.put("temporaryPassword", tempPassword);
        publishEmail(email, "ANCHOR_INTAKE_INVITE", "ANCHOR_INTAKE", app.getId(), data);
    }

    private void publishSendBackEmail(LoanApplication app, String notes) {
        String email = ApplicationPartyResolver.resolveEmail(app);
        if (email.isBlank()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("anchorName", ApplicationPartyResolver.resolveDisplayName(app));
        data.put("applicationNumber", app.getApplicationNumber());
        data.put("notes", notes != null && !notes.isBlank() ? notes : "(No notes provided)");
        data.put("portalUrl", trimSlash(anchorPortalUrl) + "/onboarding/continue");
        publishEmail(email, "ANCHOR_APPLICATION_SENT_BACK", "ANCHOR_SEND_BACK", app.getId(), data);
    }

    private void publishResubmitAck(LoanApplication app) {
        String email = ApplicationPartyResolver.resolveEmail(app);
        if (email.isBlank()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("anchorName", ApplicationPartyResolver.resolveDisplayName(app));
        data.put("applicationNumber", app.getApplicationNumber());
        publishEmail(email, "ANCHOR_INTAKE_RESUBMITTED", "ANCHOR_RESUBMIT", app.getId(), data);
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

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
