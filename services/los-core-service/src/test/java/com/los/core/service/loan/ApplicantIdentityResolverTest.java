package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicantIdentityResolverTest {

    @Test
    void resolvePanFromPersonalInfoForBorrower() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("panNumber", "abcde1234f"))
                .build();
        assertEquals("ABCDE1234F", ApplicantIdentityResolver.resolvePanNumber(app));
    }

    @Test
    void resolvePanFromEntityPanForAnchor() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.ANCHOR)
                .businessInfo(Map.of("entityPan", "aaacb1234d"))
                .build();
        assertEquals("AAACB1234D", ApplicantIdentityResolver.resolvePanNumber(app));
    }

    @Test
    void buildBureauBorrowerInfoIncludesCanonicalPanForAnchor() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.ANCHOR)
                .businessInfo(Map.of("entityPan", "AAACB1234D", "corporateName", "Acme Corp"))
                .build();
        Map<String, Object> info = ApplicantIdentityResolver.buildBureauBorrowerInfo(app);
        assertEquals("AAACB1234D", info.get("panNumber"));
        assertEquals("Acme Corp", info.get("corporateName"));
    }

    @Test
    void enrichKycPayloadPrefersRequestPanWhenProvided() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.ANCHOR)
                .businessInfo(Map.of("entityPan", "AAACB1234D"))
                .build();
        Map<String, Object> merged = ApplicantIdentityResolver.enrichKycPayload(app, Map.of("panNumber", "BBBBB9999B"));
        assertEquals("BBBBB9999B", merged.get("panNumber"));
    }

    @Test
    void enrichKycPayloadMapsDlNumberAliasForKarza() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("dlNumber", "hr0619900012345", "voterId", "abc1234567"))
                .build();
        Map<String, Object> merged = ApplicantIdentityResolver.enrichKycPayload(app, Map.of());
        assertEquals("HR0619900012345", merged.get("dlNo"));
        assertEquals("HR0619900012345", merged.get("drivingLicenseNumber"));
        assertEquals("ABC1234567", merged.get("epicNo"));
    }
}
