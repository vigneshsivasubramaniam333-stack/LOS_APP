package com.los.plp.service;

import com.los.plp.model.entity.ProgramMaster;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramCustomFieldBridgeTest {

    @Test
    void applySystemFields_dualWritesColumns() {
        ProgramMaster program = new ProgramMaster();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(ProgramCustomFieldBridge.KEY_ANCHOR_REL_MONTHS, 12);
        fields.put(ProgramCustomFieldBridge.KEY_INTEREST_PAYMENT, "monthly");
        fields.put(ProgramCustomFieldBridge.KEY_MAX_INVOICE_VINTAGE, 45);
        fields.put(ProgramCustomFieldBridge.KEY_MAX_CMR, 6);
        fields.put(ProgramCustomFieldBridge.KEY_MIN_CIBIL, 700);
        fields.put(ProgramCustomFieldBridge.KEY_TENURE_DAYS, 90);
        fields.put("extraNote", "keep");

        ProgramCustomFieldBridge.applySystemFields(program, fields);

        assertThat(program.getAnchorRelationshipVintageMonths()).isEqualTo(12);
        assertThat(program.getInterestPayment()).isEqualTo("MONTHLY");
        assertThat(program.getMaxInvoiceVintageDays()).isEqualTo(45);
        assertThat(program.getMaxCmr()).isEqualTo(6);
        assertThat(program.getMinCibil()).isEqualTo(700);
        assertThat(program.getTenureDays()).isEqualTo(90);
    }

    @Test
    void applySystemFields_acceptsPlpCatalogKeys() {
        ProgramMaster program = new ProgramMaster();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(ProgramCustomFieldBridge.KEY_MAX_INVOICE_AGE_PLP, 40);
        fields.put(ProgramCustomFieldBridge.KEY_MAX_TENURE_PLP, 75);

        ProgramCustomFieldBridge.applySystemFields(program, fields);

        assertThat(program.getMaxInvoiceVintageDays()).isEqualTo(40);
        assertThat(program.getTenureDays()).isEqualTo(75);
    }

    @Test
    void fromScalars_buildsMapWhenCustomFieldsAbsent() {
        Map<String, Object> map = ProgramCustomFieldBridge.fromScalars(
                60, 10, "UPFRONT", 30, 5, 650);
        assertThat(map)
                .containsEntry(ProgramCustomFieldBridge.KEY_TENURE_DAYS, 60)
                .containsEntry(ProgramCustomFieldBridge.KEY_ANCHOR_REL_MONTHS, 10)
                .containsEntry(ProgramCustomFieldBridge.KEY_INTEREST_PAYMENT, "UPFRONT")
                .containsEntry(ProgramCustomFieldBridge.KEY_MAX_INVOICE_VINTAGE, 30)
                .containsEntry(ProgramCustomFieldBridge.KEY_MAX_CMR, 5)
                .containsEntry(ProgramCustomFieldBridge.KEY_MIN_CIBIL, 650);
    }

    @Test
    void mergeForResponse_backfillsFromColumnsWhenJsonEmpty() {
        ProgramMaster program = new ProgramMaster();
        program.setTenureDays(120);
        program.setMaxCmr(7);
        program.setCustomFields(Map.of());

        Map<String, Object> merged = ProgramCustomFieldBridge.mergeForResponse(program);

        assertThat(merged)
                .containsEntry(ProgramCustomFieldBridge.KEY_TENURE_DAYS, 120)
                .containsEntry(ProgramCustomFieldBridge.KEY_MAX_CMR, 7);
    }

    @Test
    void mergeForResponse_prefersStoredCustomFields() {
        ProgramMaster program = new ProgramMaster();
        program.setTenureDays(120);
        program.setCustomFields(Map.of(ProgramCustomFieldBridge.KEY_TENURE_DAYS, 45));

        Map<String, Object> merged = ProgramCustomFieldBridge.mergeForResponse(program);

        assertThat(merged.get(ProgramCustomFieldBridge.KEY_TENURE_DAYS)).isEqualTo(45);
    }
}
