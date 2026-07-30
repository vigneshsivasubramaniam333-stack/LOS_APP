package com.los.core.service.borrower;

import com.los.core.config.RabbitMQConfig;
import com.los.core.config.LosWorkflowProperties;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.response.ApplicationPartyResponse;
import com.los.core.model.entity.ApplicationParty;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationPartyRole;
import com.los.core.model.entity.LosUser;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.IntakeOwner;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.auth.BorrowerAccountProvisioningService;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.ApplicationPartyService;
import com.los.core.service.loan.CoApplicantWorkflowConfig;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.core.service.loan.InvoiceDiscountingSanctionDefaultsService;
import com.los.core.service.loan.LoanApplicationFlowService;
import com.los.core.service.loan.ApplicationInputChangeTracker;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowerIntakeDelegationService {

    private final LoanApplicationRepository applicationRepository;
    private final BorrowerAccountProvisioningService borrowerAccountProvisioningService;
    private final AuditService auditService;
    private final RabbitTemplate rabbitTemplate;
    private final LosWorkflowProperties workflowProperties;
    private final InvoiceDiscountingSanctionDefaultsService invoiceDiscountingSanctionDefaultsService;
    private final ApplicationInputChangeTracker applicationInputChangeTracker;
    private final ApplicationPartyService applicationPartyService;

    @Value("${los.borrower-ui-url:http://localhost:5173/borrower}")
    private String borrowerUiUrl;

    @Transactional
    public LoanApplication saveDraftAndNotifyBorrower(UUID applicationId, int completedStep) {
        if (!workflowProperties.getBorrowerDelegation().isEnabled()) {
            throw new BusinessRuleException("Borrower intake delegation is disabled");
        }
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (app.getIntakeSegment() == IntakeSegment.ANCHOR) {
            throw new BusinessRuleException("Anchor onboarding cannot be delegated to borrower portal");
        }
        if (app.getStatus() != ApplicationStatus.DRAFT && app.getStatus() != ApplicationStatus.CONSENT_PENDING) {
            throw new BusinessRuleException(
                    "Save draft & notify is only allowed from DRAFT or CONSENT_PENDING. Current: "
                            + app.getStatus());
        }

        applicationPartyService.ensurePrimaryParty(app);
        applicationPartyService.syncPrimaryFromApplication(app);
        CoApplicantWorkflowConfig.Settings settings = applicationPartyService.resolveSettings(app);
        if (settings.enabled() && settings.notifyAllOnInvite()) {
            return notifyAllApplicants(app, completedStep, settings);
        }
        return notifyPrimaryOnly(app, completedStep);
    }

    private LoanApplication notifyPrimaryOnly(LoanApplication app, int completedStep) {
        String email = ApplicationPartyResolver.resolveEmail(app);
        if (email.isBlank()) {
            throw new BusinessRuleException("Borrower email is required before notifying the borrower");
        }
        String name = ApplicationPartyResolver.resolveDisplayName(app);
        String mobile = ApplicationPartyResolver.resolveMobile(app);

        Optional<BorrowerAccountProvisioningService.BorrowerProvisionResult> provisioned =
                borrowerAccountProvisioningService.findOrCreateBorrowerWithCredential(name, email, mobile);
        if (provisioned.isPresent()) {
            app.setCustomerId(provisioned.get().user().getId());
            applicationPartyService.markPartyInvited(
                    applicationPartyService.requirePrimary(app.getId()), provisioned.get().user().getId());
        }

        app.setStatus(ApplicationStatus.CONSENT_PENDING);
        app.setIntakeOwner(IntakeOwner.BORROWER);
        app.setIntakeCompletedStep(completedStep);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);

        auditService.logEvent(app.getId(), "FLOW", "BORROWER_INTAKE_DELEGATED", null,
                Map.of("status", ApplicationStatus.DRAFT.name()),
                Map.of("status", ApplicationStatus.CONSENT_PENDING.name(), "completedStep", completedStep),
                "Staff saved draft and invited borrower to complete intake");

        String tempPassword = provisioned
                .map(BorrowerAccountProvisioningService.BorrowerProvisionResult::temporaryPasswordForEmail)
                .filter(p -> p != null && !p.isBlank())
                .orElse("Use your existing password or forgot-password to reset");

        publishInviteEmail(app, applicationPartyService.requirePrimary(app.getId()), email, name, tempPassword);
        return app;
    }

    private LoanApplication notifyAllApplicants(
            LoanApplication app, int completedStep, CoApplicantWorkflowConfig.Settings settings) {
        UUID applicationId = app.getId();
        List<ApplicationParty> parties = applicationPartyService.listParties(applicationId).stream()
                .map(r -> applicationPartyService.requireParty(applicationId, r.getId()))
                .toList();
        long coCount = parties.stream().filter(p -> p.getRole() == ApplicationPartyRole.CO_APPLICANT).count();
        if (coCount < settings.minCoApplicants()) {
            throw new BusinessRuleException(
                    "At least " + settings.minCoApplicants() + " co-applicant(s) required before notify");
        }
        assertDistinctApplicantContacts(app, parties);
        for (ApplicationParty party : parties) {
            String email = ApplicationPartyService.resolvePartyEmail(party);
            String name = ApplicationPartyService.resolvePartyName(party);
            String mobile = ApplicationPartyService.resolvePartyMobile(party);
            if (email.isBlank()) {
                throw new BusinessRuleException("Email required for " + party.getRole() + " applicant before notify");
            }
            Optional<BorrowerAccountProvisioningService.BorrowerProvisionResult> provisioned =
                    borrowerAccountProvisioningService.findOrCreateBorrowerWithCredential(name, email, mobile);
            if (provisioned.isEmpty()) {
                continue;
            }
            UUID userId = provisioned.get().user().getId();
            if (party.getRole() == ApplicationPartyRole.PRIMARY) {
                app.setCustomerId(userId);
            }
            applicationPartyService.markPartyInvited(party, userId);
            String tempPassword = provisioned.get().temporaryPasswordForEmail();
            if (tempPassword == null || tempPassword.isBlank()) {
                tempPassword = "Use your existing password or forgot-password to reset";
            }
            publishInviteEmail(app, party, email, name, tempPassword);
        }
        app.setStatus(ApplicationStatus.CONSENT_PENDING);
        app.setIntakeOwner(IntakeOwner.BORROWER);
        app.setIntakeCompletedStep(completedStep);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(app.getId(), "FLOW", "ALL_APPLICANTS_INTAKE_DELEGATED", null,
                Map.of("status", ApplicationStatus.DRAFT.name()),
                Map.of("status", ApplicationStatus.CONSENT_PENDING.name(),
                        "completedStep", completedStep, "partyCount", parties.size()),
                "Staff saved draft and invited all applicants to complete intake");
        return app;
    }

    @Transactional
    public LoanApplication submitBorrowerIntake(UUID applicationId, UUID borrowerUserId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (borrowerUserId != null && !borrowerUserId.equals(app.getCustomerId())) {
            Optional<ApplicationParty> party = applicationPartyService.findPartyForUser(applicationId, borrowerUserId);
            if (party.isPresent() && party.get().getRole() == ApplicationPartyRole.CO_APPLICANT) {
                submitCoApplicantIntake(applicationId, party.get().getId(), borrowerUserId);
                return applicationRepository.findById(applicationId).orElse(app);
            }
            throw new BusinessRuleException("You can only submit your own application");
        }
        if (app.getStatus() != ApplicationStatus.CONSENT_PENDING
                && app.getStatus() != ApplicationStatus.BORROWER_SENT_BACK) {
            throw new BusinessRuleException(
                    "Borrower submit requires CONSENT_PENDING or BORROWER_SENT_BACK. Current: "
                            + app.getStatus());
        }
        if (app.getIntakeOwner() != null && app.getIntakeOwner() != IntakeOwner.BORROWER) {
            throw new BusinessRuleException("This application was not delegated to the borrower portal");
        }
        // Heal older rows notified before intakeOwner was persisted/exposed on the API.
        if (app.getIntakeOwner() == null) {
            app.setIntakeOwner(IntakeOwner.BORROWER);
        }
        // Reject a requested amount above the program's Max. dealer limit before it reaches sanction / PLP link.
        invoiceDiscountingSanctionDefaultsService.validateRequestedAmountWithinProgramLimit(app);
        applicationInputChangeTracker.refreshIntakeChangeSinceSendBack(app);
        // Always mark the PRIMARY party submitted when the primary borrower submits — even if
        // party.userId was stale — so portal "Continue" hides while co-applicants finish.
        applicationPartyService.findPartyForUser(applicationId, borrowerUserId)
                .or(() -> applicationPartyService.findParty(applicationId, null))
                .ifPresent(party -> {
                    if (party.getRole() != ApplicationPartyRole.PRIMARY
                            && (borrowerUserId == null || !borrowerUserId.equals(party.getUserId()))) {
                        return;
                    }
                    if (borrowerUserId != null && party.getUserId() == null) {
                        party.setUserId(borrowerUserId);
                    }
                    applicationPartyService.markPartySubmitted(party);
                });
        if (applicationPartyService.isMultiPartyEnabled(app) && !allCoApplicantsSubmitted(applicationId)) {
            app.setUpdatedAt(Instant.now());
            return applicationRepository.save(app);
        }
        app.setStatus(ApplicationStatus.BORROWER_SUBMITTED);
        app.setBorrowerSentBackNotes(null);
        app.setUpdatedAt(Instant.now());
        app = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "BORROWER_SUBMITTED", null,
                Map.of("status", ApplicationStatus.CONSENT_PENDING.name()),
                Map.of("status", ApplicationStatus.BORROWER_SUBMITTED.name()),
                "Borrower completed delegated intake and submitted for admin review");
        return app;
    }

    @Transactional
    public ApplicationPartyResponse submitCoApplicantIntake(UUID applicationId, UUID partyId, UUID userId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        ApplicationParty party = applicationPartyService.requireParty(applicationId, partyId);
        if (party.getRole() != ApplicationPartyRole.CO_APPLICANT) {
            throw new BusinessRuleException("Use primary borrower submit for the primary applicant");
        }
        if (userId != null && party.getUserId() != null && !userId.equals(party.getUserId())) {
            throw new BusinessRuleException("You can only submit your own co-applicant profile");
        }
        applicationPartyService.updatePartyPersonalInfo(applicationId, partyId,
                party.getPersonalInfo() != null ? party.getPersonalInfo() : Map.of());
        applicationPartyService.markPartySubmitted(applicationPartyService.requireParty(applicationId, partyId));
        auditService.logEvent(applicationId, "FLOW", "CO_APPLICANT_SUBMITTED", userId, null,
                Map.of("partyId", partyId.toString(), "intakeStatus", "SUBMITTED"),
                "Co-applicant completed intake");
        if (app.getStatus() == ApplicationStatus.CONSENT_PENDING
                && primarySubmitted(applicationId) && allCoApplicantsSubmitted(applicationId)) {
            app.setStatus(ApplicationStatus.BORROWER_SUBMITTED);
            app.setUpdatedAt(Instant.now());
            applicationRepository.save(app);
        }
        return applicationPartyService.toResponse(applicationPartyService.requireParty(applicationId, partyId));
    }

    private boolean allCoApplicantsSubmitted(UUID applicationId) {
        return applicationPartyService.listParties(applicationId).stream()
                .filter(p -> p.getRole() == ApplicationPartyRole.CO_APPLICANT)
                .allMatch(p -> p.getIntakeStatus() != null && p.getIntakeStatus().name().matches(
                        "SUBMITTED|KYC_COMPLETE|ESIGN_PENDING|ESIGN_COMPLETE"));
    }

    private boolean primarySubmitted(UUID applicationId) {
        return applicationPartyService.listParties(applicationId).stream()
                .filter(p -> p.getRole() == ApplicationPartyRole.PRIMARY)
                .anyMatch(p -> p.getIntakeStatus() != null && p.getIntakeStatus().name().matches(
                        "SUBMITTED|KYC_COMPLETE|ESIGN_PENDING|ESIGN_COMPLETE"));
    }

    private void assertDistinctApplicantContacts(LoanApplication app, List<ApplicationParty> parties) {
        java.util.Set<String> emails = new java.util.HashSet<>();
        java.util.Set<String> mobiles = new java.util.HashSet<>();
        for (ApplicationParty party : parties) {
            String email = ApplicationPartyService.resolvePartyEmail(party);
            if (email.isBlank() && party.getRole() == ApplicationPartyRole.PRIMARY) {
                email = ApplicationPartyResolver.resolveEmail(app);
            }
            String mobile = ApplicationPartyService.resolvePartyMobile(party);
            if (mobile.isBlank() && party.getRole() == ApplicationPartyRole.PRIMARY) {
                mobile = ApplicationPartyResolver.resolveMobile(app);
            }
            String emailNorm = email == null ? "" : email.trim().toLowerCase();
            String mobileDigits = mobile == null ? "" : mobile.replaceAll("\\D", "");
            if (!emailNorm.isBlank() && !emails.add(emailNorm)) {
                throw new BusinessRuleException("Each applicant must use a different email before notify.");
            }
            if (mobileDigits.length() >= 10 && !mobiles.add(mobileDigits)) {
                throw new BusinessRuleException("Each applicant must use a different mobile number before notify.");
            }
        }
    }

    private void publishInviteEmail(
            LoanApplication app, ApplicationParty party, String email, String name, String tempPasswordHint) {
        String portalUrl = borrowerUiUrl + "/apply?resume=" + app.getId();
        if (party != null && party.getRole() == ApplicationPartyRole.CO_APPLICANT && party.getId() != null) {
            portalUrl += "&partyId=" + party.getId();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("borrowerName", name != null && !name.isBlank() ? name : "Customer");
        data.put("applicationNumber", app.getApplicationNumber());
        data.put("portalUrl", portalUrl);
        data.put("loginEmail", email);
        data.put("temporaryPassword", tempPasswordHint);
        data.put("applicantRole", party != null ? party.getRole().name() : ApplicationPartyRole.PRIMARY.name());

        RoutingEmailEvent event = RoutingEmailEvent.builder()
                .channel("EMAIL")
                .recipient(email.trim())
                .templateCode("BORROWER_INTAKE_INVITE")
                .eventType("BORROWER_INTAKE")
                .applicationId(app.getId())
                .templateData(data)
                .build();
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE, "notification.email.borrower_intake", event);
        } catch (Exception e) {
            log.error("Borrower intake invite email failed for {}: {}", app.getId(), e.getMessage());
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
