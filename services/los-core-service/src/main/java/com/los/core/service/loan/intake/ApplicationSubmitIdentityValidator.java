package com.los.core.service.loan.intake;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.loan.ApplicantIdentityResolver;
import com.los.core.service.loan.ApplicationPartyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blocks submission when the application's email / PAN / GSTN already belongs to a <em>different</em>
 * borrower (different {@code customerId}). Because the customer is resolved from email/mobile before
 * this runs, the same borrower re-applying shares a {@code customerId} and is allowed; a reused PAN or
 * GSTN tied to a different identity is rejected.
 *
 * <p>Read-only and side-effect free; it only reads other applications and throws on conflict.</p>
 */
@Component
@RequiredArgsConstructor
public class ApplicationSubmitIdentityValidator {

    private final LoanApplicationRepository applicationRepository;

    public void validateNoDuplicateIdentity(LoanApplication app) {
        UUID selfId = app.getId();
        UUID customerId = app.getCustomerId();

        String email = ApplicationPartyResolver.resolveEmail(app);
        if (!email.isBlank()) {
            blockIfDifferentCustomer(
                    applicationRepository.findOthersByEmail(selfId, email), customerId,
                    "email", email,
                    "This email is already used by another borrower's application.");
        }

        String pan = ApplicantIdentityResolver.resolvePanNumber(app);
        if (!pan.isBlank()) {
            blockIfDifferentCustomer(
                    applicationRepository.findOthersByPan(selfId, pan), customerId,
                    "panNumber", pan,
                    "This PAN is already used by another borrower's application.");
        }

        String gstin = resolveGstin(app);
        if (!gstin.isBlank()) {
            blockIfDifferentCustomer(
                    applicationRepository.findOthersByGstin(selfId, gstin), customerId,
                    "gstin", gstin,
                    "This GSTIN is already used by another borrower's application.");
        }
    }

    private void blockIfDifferentCustomer(
            List<LoanApplication> others, UUID customerId, String field, String value, String message) {
        for (LoanApplication other : others) {
            if (customerId == null || !customerId.equals(other.getCustomerId())) {
                throw new BusinessRuleException(
                        message,
                        "DUPLICATE_" + field.toUpperCase(),
                        "SUBMIT_APPLICATION",
                        Map.of("field", field, "value", value,
                                "conflictingApplicationNumber",
                                other.getApplicationNumber() != null ? other.getApplicationNumber() : ""));
            }
        }
    }

    private static String resolveGstin(LoanApplication app) {
        String fromPersonal = stringValue(app.getPersonalInfo());
        if (!fromPersonal.isBlank()) {
            return fromPersonal;
        }
        return stringValueBusiness(app);
    }

    private static String stringValue(Map<String, Object> personalInfo) {
        if (personalInfo == null) {
            return "";
        }
        Object v = personalInfo.get("gstin");
        return v == null ? "" : String.valueOf(v).trim().toUpperCase();
    }

    private static String stringValueBusiness(LoanApplication app) {
        Map<String, Object> bi = app.getBusinessInfo();
        if (bi == null) {
            return "";
        }
        Object v = bi.get("gstin");
        return v == null ? "" : String.valueOf(v).trim().toUpperCase();
    }
}
