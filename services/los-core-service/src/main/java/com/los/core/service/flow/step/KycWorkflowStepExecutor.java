package com.los.core.service.flow.step;

import com.los.core.model.dto.response.KycStepResultResponse;
import com.los.core.model.entity.ApplicationParty;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationPartyRole;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.PartyKycStatus;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.flow.event.AutoBureauPullRequestedEvent;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.loan.ApplicationInputChangeTracker;
import com.los.core.service.loan.ApplicationPartyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link FlowStepType#KYC_WORKFLOW} — delegates to {@link IKycOrchestrationService#executeWorkflow}.
 * When co-applicants exist, runs KYC once per party and aggregates outcomes.
 * Single-applicant behaviour is unchanged (one workflow run, no partyId required).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KycWorkflowStepExecutor implements IStepExecutor {

    private final LoanApplicationRepository applicationRepository;
    private final IKycOrchestrationService kycOrchestrationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ApplicationInputChangeTracker applicationInputChangeTracker;
    private final ApplicationPartyService applicationPartyService;

    @Override
    public boolean supports(String stepType) {
        return FlowStepType.KYC_WORKFLOW.equals(stepType);
    }

    @Override
    @Transactional
    public StepResult execute(UUID applicationId, Map<String, Object> context) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.los.core.exception.ResourceNotFoundException("Application not found: " + applicationId));
        if (app.getStatus() != ApplicationStatus.KYC_IN_PROGRESS) {
            throw new com.los.core.exception.BusinessRuleException(
                    String.format("Cannot run KYC — application must be in KYC_IN_PROGRESS status. Current: %s", app.getStatus()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> kycPayload = context != null && context.get("kycPayload") != null
                ? (Map<String, Object>) context.get("kycPayload")
                : Map.of();

        List<ApplicationParty> parties = applicationPartyService.listPartyEntities(applicationId);
        boolean multiParty = applicationPartyService.isMultiPartyEnabled(app)
                && parties.stream().anyMatch(p -> p.getRole() == ApplicationPartyRole.CO_APPLICANT);

        List<KycStepResultResponse> results = new ArrayList<>();
        boolean allPassed;

        if (!multiParty) {
            results.addAll(kycOrchestrationService.executeWorkflow(applicationId, kycPayload));
            long failureCount = results.stream().filter(r -> r.getOutcome() == StepOutcome.FAILURE).count();
            allPassed = failureCount == 0;
        } else {
            boolean anyPartyFailed = false;
            applicationPartyService.ensurePrimaryParty(app);
            List<ApplicationParty> ordered = applicationPartyService.listPartyEntities(applicationId);

            for (ApplicationParty party : ordered) {
                Map<String, Object> partyPayload = new HashMap<>();
                partyPayload.put("partyId", party.getId().toString());
                // Primary can use UI-edited payload; co-applicants use stored party personalInfo only
                // (executeWorkflow merges partyIdentityMap then payload — avoid overwriting with primary fields).
                if (party.getRole() == ApplicationPartyRole.PRIMARY && kycPayload != null) {
                    partyPayload.putAll(kycPayload);
                    partyPayload.put("partyId", party.getId().toString());
                }

                List<KycStepResultResponse> partyResults =
                        kycOrchestrationService.executeWorkflow(applicationId, partyPayload);
                results.addAll(partyResults);

                boolean partyFailed = partyResults.stream()
                        .anyMatch(r -> r.getOutcome() == StepOutcome.FAILURE && !r.isOverridden());
                ApplicationParty refreshed = applicationPartyService.requireParty(applicationId, party.getId());
                if (partyFailed) {
                    anyPartyFailed = true;
                    applicationPartyService.markPartyKycStatus(refreshed, PartyKycStatus.FAILED);
                    log.warn("KYC failed for party {} ({}) on application {}",
                            party.getId(), party.getRole(), app.getApplicationNumber());
                } else if (!partyResults.isEmpty()) {
                    applicationPartyService.markPartyKycStatus(refreshed, PartyKycStatus.COMPLETE);
                }
            }
            allPassed = !anyPartyFailed;
        }

        long successCount = results.stream().filter(r -> r.getOutcome() == StepOutcome.SUCCESS).count();
        long failureCount = results.stream().filter(r -> r.getOutcome() == StepOutcome.FAILURE).count();

        if (!allPassed) {
            app.setStatus(ApplicationStatus.KYC_FAILED);
            applicationRepository.save(app);
            log.warn("KYC workflow for {} — {} failures out of {} steps (multiParty={})",
                    app.getApplicationNumber(), failureCount, results.size(), multiParty);
        } else {
            applicationInputChangeTracker.recordKycVerifiedSnapshot(app);
            applicationRepository.save(app);
            eventPublisher.publishEvent(
                    new AutoBureauPullRequestedEvent(applicationId, "KYC_WORKFLOW_SUCCESS"));
        }

        Map<String, Object> out = Map.of(
                "applicationId", applicationId,
                "applicationNumber", app.getApplicationNumber(),
                "status", app.getStatus().name(),
                "totalSteps", results.size(),
                "successCount", successCount,
                "failureCount", failureCount,
                "allPassed", allPassed,
                "multiParty", multiParty,
                "results", results
        );
        return StepResult.ok(out);
    }
}
