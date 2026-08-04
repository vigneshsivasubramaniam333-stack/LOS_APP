package com.los.plp.service;

import com.los.plp.model.entity.ProgramMaster;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Dual-writes system custom-field keys onto typed {@link ProgramMaster} columns so existing
 * processing (vintage, tenor, sync scalars) keeps working without reading the JSON bag.
 */
public final class ProgramCustomFieldBridge {

    public static final String KEY_ANCHOR_REL_MONTHS = "anchorRelationshipVintageMonths";
    public static final String KEY_INTEREST_PAYMENT = "interestPayment";
    public static final String KEY_MAX_INVOICE_VINTAGE = "maxInvoiceVintageDays";
    /** PLP catalog / config storage key for invoice age. */
    public static final String KEY_MAX_INVOICE_AGE_PLP = "maxInvoiceAgeDays";
    public static final String KEY_MAX_CMR = "maxCmr";
    public static final String KEY_MIN_CIBIL = "minCibil";
    public static final String KEY_TENURE_DAYS = "tenureDays";
    /** PLP catalog / column dual-write key for max tenure. */
    public static final String KEY_MAX_TENURE_PLP = "maxTenureDays";

    private ProgramCustomFieldBridge() {
    }

    public static void applySystemFields(ProgramMaster program, Map<String, Object> customFields) {
        if (program == null || customFields == null || customFields.isEmpty()) {
            return;
        }
        if (customFields.containsKey(KEY_ANCHOR_REL_MONTHS)) {
            program.setAnchorRelationshipVintageMonths(toInteger(customFields.get(KEY_ANCHOR_REL_MONTHS)));
        }
        if (customFields.containsKey(KEY_INTEREST_PAYMENT)) {
            program.setInterestPayment(normalizeInterestPayment(stringOrNull(customFields.get(KEY_INTEREST_PAYMENT))));
        }
        Object invoiceAge = firstPresent(customFields, KEY_MAX_INVOICE_VINTAGE, KEY_MAX_INVOICE_AGE_PLP);
        if (invoiceAge != null) {
            program.setMaxInvoiceVintageDays(toInteger(invoiceAge));
        }
        if (customFields.containsKey(KEY_MAX_CMR)) {
            program.setMaxCmr(toInteger(customFields.get(KEY_MAX_CMR)));
        }
        if (customFields.containsKey(KEY_MIN_CIBIL)) {
            program.setMinCibil(toInteger(customFields.get(KEY_MIN_CIBIL)));
        }
        Object tenure = firstPresent(customFields, KEY_TENURE_DAYS, KEY_MAX_TENURE_PLP);
        if (tenure != null) {
            program.setTenureDays(toInteger(tenure));
        }
    }

    /** Build map from request scalars when client did not send customFields. */
    public static Map<String, Object> fromScalars(
            Integer tenureDays,
            Integer anchorRelationshipVintageMonths,
            String interestPayment,
            Integer maxInvoiceVintageDays,
            Integer maxCmr,
            Integer minCibil) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (tenureDays != null) {
            m.put(KEY_TENURE_DAYS, tenureDays);
        }
        if (anchorRelationshipVintageMonths != null) {
            m.put(KEY_ANCHOR_REL_MONTHS, anchorRelationshipVintageMonths);
        }
        if (interestPayment != null && !interestPayment.isBlank()) {
            m.put(KEY_INTEREST_PAYMENT, interestPayment.trim());
        }
        if (maxInvoiceVintageDays != null) {
            m.put(KEY_MAX_INVOICE_VINTAGE, maxInvoiceVintageDays);
        }
        if (maxCmr != null) {
            m.put(KEY_MAX_CMR, maxCmr);
        }
        if (minCibil != null) {
            m.put(KEY_MIN_CIBIL, minCibil);
        }
        return m;
    }

    /** Merge entity columns + stored JSON for API responses (both LOS and PLP system key names). */
    public static Map<String, Object> mergeForResponse(ProgramMaster program) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (program.getCustomFields() != null) {
            m.putAll(program.getCustomFields());
        }
        putIfAbsent(m, KEY_TENURE_DAYS, program.getTenureDays());
        putIfAbsent(m, KEY_MAX_TENURE_PLP, program.getTenureDays());
        putIfAbsent(m, KEY_ANCHOR_REL_MONTHS, program.getAnchorRelationshipVintageMonths());
        putIfAbsent(m, KEY_INTEREST_PAYMENT, program.getInterestPayment());
        putIfAbsent(m, KEY_MAX_INVOICE_VINTAGE, program.getMaxInvoiceVintageDays());
        putIfAbsent(m, KEY_MAX_INVOICE_AGE_PLP, program.getMaxInvoiceVintageDays());
        putIfAbsent(m, KEY_MAX_CMR, program.getMaxCmr());
        putIfAbsent(m, KEY_MIN_CIBIL, program.getMinCibil());
        return m;
    }

    private static Object firstPresent(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            if (m.containsKey(k) && !isBlankStored(m.get(k))) {
                return m.get(k);
            }
        }
        return null;
    }

    private static boolean isBlankStored(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    private static void putIfAbsent(Map<String, Object> m, String key, Object value) {
        if (value == null) {
            return;
        }
        if (!m.containsKey(key) || m.get(key) == null || String.valueOf(m.get(key)).isBlank()) {
            m.put(key, value);
        }
    }

    public static String normalizeInterestPayment(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("REARENDED".equals(s) || "REAR_END".equals(s)) {
            s = "REAR_ENDED";
        }
        if ("UPFRONT".equals(s) || "MONTHLY".equals(s) || "REAR_ENDED".equals(s)) {
            return s;
        }
        return s;
    }

    private static Integer toInteger(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof BigDecimal bd) {
            return bd.intValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        return (int) Double.parseDouble(s);
    }

    private static String stringOrNull(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
