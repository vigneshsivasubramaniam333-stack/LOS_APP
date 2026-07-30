package com.los.core.service.loan;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoApplicantWorkflowConfigTest {

    @Test
    void disabledByDefaultWhenMissing() {
        var settings = CoApplicantWorkflowConfig.fromIntakeConfig(Map.of(), sampleApp("TERM_LOAN"));
        assertThat(settings.enabled()).isFalse();
    }

    @Test
    void enabledWhenConfigured() {
        Map<String, Object> intake = new LinkedHashMap<>();
        intake.put("coApplicant", Map.of(
                "enabled", true,
                "minCoApplicants", 1,
                "maxCoApplicants", 2,
                "coApplicantKycSteps", List.of("PAN_VERIFY", "AADHAAR_OTP"),
                "requireAllEsignBeforeDisbursement", true,
                "underwritingParty", "PRIMARY"
        ));
        var settings = CoApplicantWorkflowConfig.fromIntakeConfig(intake, sampleApp("TERM_LOAN"));
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.minCoApplicants()).isEqualTo(1);
        assertThat(settings.maxCoApplicants()).isEqualTo(2);
        assertThat(settings.coApplicantKycSteps()).containsExactly("PAN_VERIFY", "AADHAAR_OTP");
        assertThat(settings.underwritingParty()).isEqualTo("PRIMARY");
    }

    @Test
    void forceDisabledForInvoiceDiscounting() {
        Map<String, Object> intake = new LinkedHashMap<>();
        intake.put("coApplicant", Map.of("enabled", true, "maxCoApplicants", 3));
        var settings = CoApplicantWorkflowConfig.fromIntakeConfig(
                intake,
                sampleApp(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING));
        assertThat(settings.enabled()).isFalse();
    }

    @Test
    void forceDisabledForAnchor() {
        Map<String, Object> intake = Map.of("coApplicant", Map.of("enabled", true));
        LoanApplication app = sampleApp("TERM_LOAN");
        app.setIntakeSegment(IntakeSegment.ANCHOR);
        var settings = CoApplicantWorkflowConfig.fromIntakeConfig(intake, app);
        assertThat(settings.enabled()).isFalse();
    }

    @Test
    void forceDisabledInIntakeConfigMutatesCopy() {
        Map<String, Object> intake = new LinkedHashMap<>();
        intake.put("policy", "WORKFLOW_DRIVEN");
        intake.put("coApplicant", Map.of("enabled", true));
        Map<String, Object> sanitized = CoApplicantWorkflowConfig.forceDisabledInIntakeConfig(intake);
        @SuppressWarnings("unchecked")
        Map<String, Object> co = (Map<String, Object>) sanitized.get("coApplicant");
        assertThat(co.get("enabled")).isEqualTo(false);
        assertThat(intake.get("coApplicant")).isEqualTo(Map.of("enabled", true));
    }

    private static LoanApplication sampleApp(String product) {
        return LoanApplication.builder()
                .loanProduct(product)
                .intakeSegment(IntakeSegment.BORROWER)
                .build();
    }
}
