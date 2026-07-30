package com.los.core.service.loan;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.UpsertApplicationPartiesRequest;
import com.los.core.model.dto.response.ApplicationPartyResponse;
import com.los.core.model.entity.ApplicationParty;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.ApplicationPartyRole;
import com.los.core.model.enums.PartyEsignStatus;
import com.los.core.model.enums.PartyIntakeStatus;
import com.los.core.model.enums.PartyKycStatus;
import com.los.core.repository.ApplicationPartyRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.loan.intake.ApplicationSubmitIdentityValidator;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ApplicationPartyService {

    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ApplicationPartyRepository partyRepository;
    private final LoanApplicationRepository applicationRepository;
    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final ApplicationSubmitIdentityValidator applicationSubmitIdentityValidator;

    @Transactional
    public ApplicationParty ensurePrimaryParty(LoanApplication app) {
        return partyRepository.findByApplicationIdAndRole(app.getId(), ApplicationPartyRole.PRIMARY)
                .orElseGet(() -> {
                    Map<String, Object> info = primaryPersonalInfoSnapshot(app);
                    ApplicationParty primary = ApplicationParty.builder()
                            .applicationId(app.getId())
                            .role(ApplicationPartyRole.PRIMARY)
                            .sequenceNo(0)
                            .userId(app.getCustomerId())
                            .personalInfo(info)
                            .intakeStatus(PartyIntakeStatus.DRAFT)
                            .kycStatus(PartyKycStatus.NOT_STARTED)
                            .esignStatus(PartyEsignStatus.NOT_STARTED)
                            .requiredForDisbursement(true)
                            .build();
                    return partyRepository.save(primary);
                });
    }

    @Transactional
    public void syncPrimaryFromApplication(LoanApplication app) {
        ApplicationParty primary = ensurePrimaryParty(app);
        primary.setPersonalInfo(primaryPersonalInfoSnapshot(app));
        if (app.getCustomerId() != null) {
            primary.setUserId(app.getCustomerId());
        }
        primary.setUpdatedAt(Instant.now());
        partyRepository.save(primary);
    }

    @Transactional(readOnly = true)
    public List<ApplicationPartyResponse> listParties(UUID applicationId) {
        ensureAppExists(applicationId);
        return partyRepository.findByApplicationIdOrderBySequenceNoAsc(applicationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationParty> listPartyEntities(UUID applicationId) {
        ensureAppExists(applicationId);
        return partyRepository.findByApplicationIdOrderBySequenceNoAsc(applicationId);
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationParty> findParty(UUID applicationId, UUID partyId) {
        if (partyId == null) {
            return partyRepository.findByApplicationIdAndRole(applicationId, ApplicationPartyRole.PRIMARY);
        }
        return partyRepository.findByIdAndApplicationId(partyId, applicationId);
    }

    @Transactional(readOnly = true)
    public ApplicationParty requireParty(UUID applicationId, UUID partyId) {
        return findParty(applicationId, partyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application party not found: " + partyId + " for application " + applicationId));
    }

    @Transactional(readOnly = true)
    public ApplicationParty requirePrimary(UUID applicationId) {
        return partyRepository.findByApplicationIdAndRole(applicationId, ApplicationPartyRole.PRIMARY)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Primary party not found for application " + applicationId));
    }

    @Transactional(readOnly = true)
    public boolean ownsApplicationAsParty(UUID applicationId, UUID userId) {
        if (userId == null) {
            return false;
        }
        LoanApplication app = applicationRepository.findById(applicationId).orElse(null);
        if (app != null && userId.equals(app.getCustomerId())) {
            return true;
        }
        return partyRepository.findByApplicationIdAndUserId(applicationId, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationParty> findPartyForUser(UUID applicationId, UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        Optional<ApplicationParty> byUser = partyRepository.findByApplicationIdAndUserId(applicationId, userId);
        if (byUser.isPresent()) {
            return byUser;
        }
        LoanApplication app = applicationRepository.findById(applicationId).orElse(null);
        if (app != null && userId.equals(app.getCustomerId())) {
            return partyRepository.findByApplicationIdAndRole(applicationId, ApplicationPartyRole.PRIMARY);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public CoApplicantWorkflowConfig.Settings resolveSettings(LoanApplication app) {
        Optional<WorkflowConfig> wf = activeWorkflowConfigService.findActiveForApplication(app);
        return CoApplicantWorkflowConfig.fromWorkflow(wf.orElse(null), app);
    }

    @Transactional(readOnly = true)
    public boolean isMultiPartyEnabled(LoanApplication app) {
        return resolveSettings(app).enabled();
    }

    @Transactional
    public List<ApplicationPartyResponse> upsertCoApplicants(UUID applicationId, UpsertApplicationPartiesRequest request) {
        LoanApplication app = ensureAppExists(applicationId);
        if (InvoiceDiscountingApplicationRules.isInvoiceDiscounting(app)) {
            throw new BusinessRuleException("Co-applicants are not supported for invoice discounting products");
        }
        CoApplicantWorkflowConfig.Settings settings = resolveSettings(app);
        ensurePrimaryParty(app);
        syncPrimaryFromApplication(app);

        List<UpsertApplicationPartiesRequest.CoApplicantDraft> drafts =
                request != null && request.getCoApplicants() != null ? request.getCoApplicants() : List.of();

        if (!settings.enabled() && !drafts.isEmpty()) {
            throw new BusinessRuleException("Co-applicants are not enabled for this product workflow");
        }
        if (settings.enabled()) {
            if (drafts.size() < settings.minCoApplicants()) {
                throw new BusinessRuleException(
                        "At least " + settings.minCoApplicants() + " co-applicant(s) required");
            }
            if (drafts.size() > settings.maxCoApplicants()) {
                throw new BusinessRuleException(
                        "At most " + settings.maxCoApplicants() + " co-applicant(s) allowed");
            }
        }

        List<ApplicationParty> existing = partyRepository.findByApplicationIdOrderBySequenceNoAsc(applicationId);
        Map<UUID, ApplicationParty> existingCoById = new LinkedHashMap<>();
        for (ApplicationParty p : existing) {
            if (p.getRole() == ApplicationPartyRole.CO_APPLICANT) {
                existingCoById.put(p.getId(), p);
            }
        }

        Set<UUID> keepIds = new HashSet<>();
        Set<String> emailsSeen = new HashSet<>();
        Set<String> mobilesSeen = new HashSet<>();
        String primaryEmail = normalizeEmail(ApplicationPartyResolver.resolveEmail(app));
        if (!primaryEmail.isBlank()) {
            emailsSeen.add(primaryEmail);
        }
        String primaryMobile = normalizeMobileDigits(ApplicationPartyResolver.resolveMobile(app));
        if (!primaryMobile.isBlank()) {
            mobilesSeen.add(primaryMobile);
        }

        int seq = 1;
        for (UpsertApplicationPartiesRequest.CoApplicantDraft draft : drafts) {
            Map<String, Object> info = draft.getPersonalInfo() != null
                    ? new HashMap<>(draft.getPersonalInfo())
                    : new HashMap<>();
            String email = normalizeEmail(firstNonBlank(
                    stringVal(info, "email"),
                    stringVal(info, "borrowerEmail"),
                    stringVal(info, "contactEmail")));
            String mobile = firstNonBlank(stringVal(info, "mobile"), stringVal(info, "phone"));
            String mobileDigits = normalizeMobileDigits(mobile);
            String name = firstNonBlank(
                    stringVal(info, "fullName"),
                    stringVal(info, "name"),
                    (stringVal(info, "firstName") + " " + stringVal(info, "lastName")).trim());

            if (name.isBlank()) {
                throw new BusinessRuleException("Co-applicant full name is required");
            }
            if (email.isBlank() || !EMAIL_RE.matcher(email).matches()) {
                throw new BusinessRuleException("Valid co-applicant email is required");
            }
            if (mobile.isBlank() || mobileDigits.length() < 10) {
                throw new BusinessRuleException("Valid co-applicant mobile is required");
            }
            if (!emailsSeen.add(email)) {
                throw new BusinessRuleException(
                        "Each applicant must use a different email. Duplicate: " + email);
            }
            if (!mobilesSeen.add(mobileDigits)) {
                throw new BusinessRuleException(
                        "Each applicant must use a different mobile number. Duplicate: " + mobile);
            }

            // Cross-application identity: email/mobile/PAN must not collide with another borrower or party.
            applicationSubmitIdentityValidator.validateFields(
                    applicationId,
                    null,
                    email,
                    mobileDigits,
                    firstNonBlank(stringVal(info, "panNumber"), stringVal(info, "pan")),
                    stringVal(info, "gstin").isBlank() ? null : stringVal(info, "gstin"));

            info.put("fullName", name);
            info.put("email", email);
            info.put("borrowerEmail", email);
            info.put("mobile", mobile);

            ApplicationParty party;
            if (draft.getId() != null && existingCoById.containsKey(draft.getId())) {
                party = existingCoById.get(draft.getId());
                keepIds.add(party.getId());
            } else {
                party = ApplicationParty.builder()
                        .applicationId(applicationId)
                        .role(ApplicationPartyRole.CO_APPLICANT)
                        .intakeStatus(PartyIntakeStatus.DRAFT)
                        .kycStatus(PartyKycStatus.NOT_STARTED)
                        .esignStatus(PartyEsignStatus.NOT_STARTED)
                        .build();
            }
            party.setSequenceNo(seq++);
            party.setPersonalInfo(info);
            party.setRequiredForDisbursement(
                    draft.getRequiredForDisbursement() == null || Boolean.TRUE.equals(draft.getRequiredForDisbursement()));
            party.setUpdatedAt(Instant.now());
            ApplicationParty saved = partyRepository.save(party);
            keepIds.add(saved.getId());
        }

        for (ApplicationParty old : existingCoById.values()) {
            if (!keepIds.contains(old.getId())) {
                partyRepository.delete(old);
            }
        }

        return partyRepository.findByApplicationIdOrderBySequenceNoAsc(applicationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationParty> partiesRequiredForDisbursement(UUID applicationId) {
        return partyRepository.findByApplicationIdOrderBySequenceNoAsc(applicationId).stream()
                .filter(ApplicationParty::isRequiredForDisbursement)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean allRequiredPartiesEsigned(UUID applicationId) {
        List<ApplicationParty> required = partiesRequiredForDisbursement(applicationId);
        if (required.isEmpty()) {
            return true;
        }
        return required.stream().allMatch(p -> p.getEsignStatus() == PartyEsignStatus.COMPLETE);
    }

    @Transactional(readOnly = true)
    public void assertAllRequiredEsigned(UUID applicationId) {
        List<ApplicationParty> pending = partiesRequiredForDisbursement(applicationId).stream()
                .filter(p -> p.getEsignStatus() != PartyEsignStatus.COMPLETE)
                .toList();
        if (!pending.isEmpty()) {
            throw new BusinessRuleException(
                    "All required applicants must complete eSign before disbursement. Pending: "
                            + pending.size(),
                    "CO_APPLICANT_ESIGN_INCOMPLETE",
                    "READY_FOR_DISBURSEMENT",
                    Map.of("pendingCount", pending.size()));
        }
    }

    @Transactional
    public void markPartyInvited(ApplicationParty party, UUID userId) {
        party.setUserId(userId);
        party.setIntakeStatus(PartyIntakeStatus.INVITED);
        party.setUpdatedAt(Instant.now());
        partyRepository.save(party);
    }

    @Transactional
    public void markPartySubmitted(ApplicationParty party) {
        party.setIntakeStatus(PartyIntakeStatus.SUBMITTED);
        party.setUpdatedAt(Instant.now());
        partyRepository.save(party);
    }

    @Transactional
    public void markPartyEsignPending(ApplicationParty party) {
        party.setEsignStatus(PartyEsignStatus.PENDING);
        party.setIntakeStatus(PartyIntakeStatus.ESIGN_PENDING);
        party.setUpdatedAt(Instant.now());
        partyRepository.save(party);
    }

    @Transactional
    public void markPartyEsignComplete(ApplicationParty party) {
        party.setEsignStatus(PartyEsignStatus.COMPLETE);
        party.setIntakeStatus(PartyIntakeStatus.ESIGN_COMPLETE);
        party.setUpdatedAt(Instant.now());
        partyRepository.save(party);
    }

    @Transactional
    public void markPartyKycStatus(ApplicationParty party, PartyKycStatus status) {
        party.setKycStatus(status);
        if (status == PartyKycStatus.COMPLETE) {
            party.setIntakeStatus(PartyIntakeStatus.KYC_COMPLETE);
        } else if (status == PartyKycStatus.IN_PROGRESS
                && party.getIntakeStatus() == PartyIntakeStatus.INVITED) {
            party.setIntakeStatus(PartyIntakeStatus.IN_PROGRESS);
        }
        party.setUpdatedAt(Instant.now());
        partyRepository.save(party);
    }

    @Transactional
    public ApplicationParty updatePartyPersonalInfo(UUID applicationId, UUID partyId, Map<String, Object> personalInfo) {
        ApplicationParty party = requireParty(applicationId, partyId);
        Map<String, Object> merged = party.getPersonalInfo() != null
                ? new HashMap<>(party.getPersonalInfo())
                : new HashMap<>();
        if (personalInfo != null) {
            merged.putAll(personalInfo);
        }
        String email = normalizeEmail(firstNonBlank(
                stringVal(merged, "email"),
                stringVal(merged, "borrowerEmail"),
                stringVal(merged, "contactEmail")));
        String mobile = firstNonBlank(stringVal(merged, "mobile"), stringVal(merged, "phone"));
        String mobileDigits = normalizeMobileDigits(mobile);
        if (!email.isBlank()) {
            merged.put("email", email);
            merged.put("borrowerEmail", email);
        }
        if (!mobile.isBlank()) {
            merged.put("mobile", mobile);
        }
        assertDistinctContactAmongParties(applicationId, party.getId(), email, mobileDigits);
        applicationSubmitIdentityValidator.validateFields(
                applicationId,
                party.getUserId(),
                email.isBlank() ? null : email,
                mobileDigits.isBlank() ? null : mobileDigits,
                firstNonBlank(stringVal(merged, "panNumber"), stringVal(merged, "pan")),
                stringVal(merged, "gstin").isBlank() ? null : stringVal(merged, "gstin"));
        party.setPersonalInfo(merged);
        if (party.getIntakeStatus() == PartyIntakeStatus.INVITED) {
            party.setIntakeStatus(PartyIntakeStatus.IN_PROGRESS);
        }
        party.setUpdatedAt(Instant.now());
        return partyRepository.save(party);
    }

    private void assertDistinctContactAmongParties(
            UUID applicationId, UUID selfPartyId, String email, String mobileDigits) {
        for (ApplicationParty other : partyRepository.findByApplicationIdOrderBySequenceNoAsc(applicationId)) {
            if (selfPartyId != null && selfPartyId.equals(other.getId())) {
                continue;
            }
            String otherEmail = normalizeEmail(resolvePartyEmail(other));
            String otherMobile = normalizeMobileDigits(resolvePartyMobile(other));
            if (!email.isBlank() && email.equals(otherEmail)) {
                throw new BusinessRuleException(
                        "Each applicant must use a different email. Duplicate: " + email);
            }
            if (!mobileDigits.isBlank() && mobileDigits.length() >= 10 && mobileDigits.equals(otherMobile)) {
                throw new BusinessRuleException(
                        "Each applicant must use a different mobile number.");
            }
        }
    }

    public ApplicationPartyResponse toResponse(ApplicationParty party) {
        Map<String, Object> info = party.getPersonalInfo() != null ? party.getPersonalInfo() : Map.of();
        return ApplicationPartyResponse.builder()
                .id(party.getId())
                .applicationId(party.getApplicationId())
                .role(party.getRole())
                .sequenceNo(party.getSequenceNo())
                .userId(party.getUserId())
                .personalInfo(party.getPersonalInfo())
                .intakeStatus(party.getIntakeStatus())
                .kycStatus(party.getKycStatus())
                .esignStatus(party.getEsignStatus())
                .requiredForDisbursement(party.isRequiredForDisbursement())
                .createdAt(party.getCreatedAt())
                .updatedAt(party.getUpdatedAt())
                .displayName(firstNonBlank(
                        stringVal(info, "fullName"),
                        stringVal(info, "name"),
                        (stringVal(info, "firstName") + " " + stringVal(info, "lastName")).trim()))
                .email(firstNonBlank(
                        stringVal(info, "email"),
                        stringVal(info, "borrowerEmail"),
                        stringVal(info, "contactEmail")))
                .mobile(firstNonBlank(stringVal(info, "mobile"), stringVal(info, "phone")))
                .build();
    }

    public static String resolvePartyEmail(ApplicationParty party) {
        if (party == null || party.getPersonalInfo() == null) {
            return "";
        }
        Map<String, Object> info = party.getPersonalInfo();
        return firstNonBlank(
                stringVal(info, "email"),
                stringVal(info, "borrowerEmail"),
                stringVal(info, "contactEmail"));
    }

    public static String resolvePartyName(ApplicationParty party) {
        if (party == null || party.getPersonalInfo() == null) {
            return "";
        }
        Map<String, Object> info = party.getPersonalInfo();
        return firstNonBlank(
                stringVal(info, "fullName"),
                stringVal(info, "name"),
                (stringVal(info, "firstName") + " " + stringVal(info, "lastName")).trim());
    }

    public static String resolvePartyMobile(ApplicationParty party) {
        if (party == null || party.getPersonalInfo() == null) {
            return "";
        }
        Map<String, Object> info = party.getPersonalInfo();
        return firstNonBlank(stringVal(info, "mobile"), stringVal(info, "phone"));
    }

    public static Map<String, Object> partyIdentityMap(ApplicationParty party) {
        if (party == null || party.getPersonalInfo() == null) {
            return Map.of();
        }
        Map<String, Object> out = new HashMap<>(party.getPersonalInfo());
        String name = firstNonBlank(
                stringVal(out, "fullName"),
                stringVal(out, "name"),
                (stringVal(out, "firstName") + " " + stringVal(out, "lastName")).trim());
        if (!name.isBlank()) {
            out.putIfAbsent("fullName", name);
            out.putIfAbsent("name", name);
        }
        String pan = firstNonBlank(stringVal(out, "panNumber"), stringVal(out, "pan"));
        if (!pan.isBlank()) {
            out.put("panNumber", pan.toUpperCase(Locale.ROOT));
        }
        String mobile = firstNonBlank(stringVal(out, "mobile"), stringVal(out, "phone"));
        if (!mobile.isBlank()) {
            out.putIfAbsent("mobile", mobile);
            out.putIfAbsent("phone", mobile);
        }
        String account = firstNonBlank(stringVal(out, "accountNumber"), stringVal(out, "bankAccountNumber"));
        if (!account.isBlank()) {
            out.putIfAbsent("accountNumber", account);
            out.putIfAbsent("bankAccountNumber", account);
        }
        String ifsc = firstNonBlank(stringVal(out, "ifsc"), stringVal(out, "ifscCode"));
        if (!ifsc.isBlank()) {
            out.putIfAbsent("ifsc", ifsc.toUpperCase(Locale.ROOT));
            out.putIfAbsent("ifscCode", ifsc.toUpperCase(Locale.ROOT));
        }
        String voter = firstNonBlank(stringVal(out, "voterId"), stringVal(out, "epicNo"));
        if (!voter.isBlank()) {
            out.putIfAbsent("voterId", voter);
            out.putIfAbsent("epicNo", voter);
        }
        String dl = firstNonBlank(stringVal(out, "dlNumber"), stringVal(out, "dlNo"), stringVal(out, "drivingLicenseNumber"));
        if (!dl.isBlank()) {
            out.putIfAbsent("dlNumber", dl);
            out.putIfAbsent("dlNo", dl);
            out.putIfAbsent("drivingLicenseNumber", dl);
        }
        return out;
    }

    private LoanApplication ensureAppExists(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    private static Map<String, Object> primaryPersonalInfoSnapshot(LoanApplication app) {
        if (ApplicationPartyResolver.isAnchor(app)) {
            return app.getBusinessInfo() != null ? new HashMap<>(app.getBusinessInfo()) : new HashMap<>();
        }
        return app.getPersonalInfo() != null ? new HashMap<>(app.getPersonalInfo()) : new HashMap<>();
    }

    private static String stringVal(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return "";
        }
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMobileDigits(String mobile) {
        if (mobile == null) {
            return "";
        }
        return mobile.replaceAll("\\D", "");
    }
}
