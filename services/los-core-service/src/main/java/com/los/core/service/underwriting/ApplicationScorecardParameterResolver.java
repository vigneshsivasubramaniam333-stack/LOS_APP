package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.service.workflow.intake.IntakeOptionCatalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Resolves APPLICATION-sourced scorecard parameters from loan application data.
 * Occupation / loan purpose return codes for string matching on the scorecard;
 * numeric scores are authored only on scorecard rows / parameterDefs.
 */
public final class ApplicationScorecardParameterResolver {

    private ApplicationScorecardParameterResolver() {
    }

    public static BigDecimal resolve(String param, LoanApplication app, Map<String, Object> intakeConfig) {
        if (param == null || app == null) {
            return null;
        }
        Map<String, Object> personal = app.getPersonalInfo() != null ? app.getPersonalInfo() : Map.of();
        return switch (param.trim().toUpperCase()) {
            case "AGE", "APPLICANT_AGE", "AGE_YEARS" -> ageYears(personal);
            // OCCUPATION / LOAN_PURPOSE are string codes — resolved via resolveString()
            default -> null;
        };
    }

    public static String resolveString(String param, LoanApplication app) {
        if (param == null || app == null) {
            return null;
        }
        Map<String, Object> personal = app.getPersonalInfo() != null ? app.getPersonalInfo() : Map.of();
        return switch (param.trim().toUpperCase()) {
            case "OCCUPATION" -> blankToNull(IntakeOptionCatalog.resolveOccupationCode(personal));
            case "LOAN_PURPOSE", "LOANPURPOSE", "PURPOSE" ->
                    blankToNull(IntakeOptionCatalog.resolveLoanPurposeCode(personal));
            default -> null;
        };
    }

    public static BigDecimal ageYears(Map<String, Object> personal) {
        String dobStr = stringValue(personal.get("dateOfBirth"));
        if (dobStr.isBlank()) {
            return null;
        }
        try {
            LocalDate dob = LocalDate.parse(dobStr);
            int age = Period.between(dob, LocalDate.now()).getYears();
            return BigDecimal.valueOf(age);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String stringValue(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
