package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves canonical identity fields (especially PAN) for KYC, bureau, and underwriting flows.
 * Anchor intake stores entity PAN on {@code businessInfo.entityPan}; borrower intake uses
 * {@code personalInfo.panNumber}.
 */
public final class ApplicantIdentityResolver {

    private ApplicantIdentityResolver() {
    }

    public static String resolvePanNumber(LoanApplication app) {
        if (app == null) {
            return "";
        }
        String fromPersonal = stringValue(app.getPersonalInfo(), "panNumber");
        if (!fromPersonal.isBlank()) {
            return fromPersonal.trim().toUpperCase();
        }
        if (isAnchor(app)) {
            String entityPan = stringValue(app.getBusinessInfo(), "entityPan");
            if (!entityPan.isBlank()) {
                return entityPan.trim().toUpperCase();
            }
        }
        return "";
    }

    /**
     * Context map for bureau providers: personal + business fields with canonical {@code panNumber}.
     */
    public static Map<String, Object> buildBureauBorrowerInfo(LoanApplication app) {
        Map<String, Object> out = new HashMap<>();
        if (app.getPersonalInfo() != null) {
            out.putAll(app.getPersonalInfo());
        }
        if (app.getBusinessInfo() != null) {
            out.putAll(app.getBusinessInfo());
        }
        String pan = resolvePanNumber(app);
        if (!pan.isBlank()) {
            out.put("panNumber", pan);
        }
        if (app.getId() != null) {
            out.put("applicationId", app.getId().toString());
        }
        if (app.getApplicationNumber() != null) {
            out.put("applicationNumber", app.getApplicationNumber());
        }
        return out;
    }

    /**
     * Merges stored application identity into the KYC workflow payload without dropping request overrides.
     */
    public static Map<String, Object> enrichKycPayload(LoanApplication app, Map<String, Object> payload) {
        Map<String, Object> merged = new HashMap<>();
        if (app.getPersonalInfo() != null) {
            merged.putAll(app.getPersonalInfo());
        }
        if (app.getBusinessInfo() != null) {
            merged.putAll(app.getBusinessInfo());
            if (isAnchor(app)) {
                copyIfPresent(merged, app.getBusinessInfo(), "entityPan", "gstin", "cin");
                copyIfPresent(merged, app.getBusinessInfo(), "bankAccountNumber", "ifscCode", "accountHolderName");
                String acct = stringValue(app.getBusinessInfo(), "bankAccountNumber");
                if (!acct.isBlank()) {
                    merged.putIfAbsent("accountNumber", acct);
                }
                String ifsc = stringValue(app.getBusinessInfo(), "ifscCode");
                if (!ifsc.isBlank()) {
                    merged.putIfAbsent("ifsc", ifsc.toUpperCase());
                }
                String corporate = stringValue(app.getBusinessInfo(), "corporateName");
                if (!corporate.isBlank()) {
                    merged.putIfAbsent("businessName", corporate);
                    merged.putIfAbsent("name", stringValue(app.getBusinessInfo(), "accountHolderName"));
                }
                String mobile = stringValue(app.getBusinessInfo(), "mobile");
                if (!mobile.isBlank()) {
                    merged.putIfAbsent("mobile", mobile);
                    merged.putIfAbsent("phone", mobile);
                }
            }
        }
        if (payload != null) {
            payload.forEach((k, v) -> {
                if (v != null && !String.valueOf(v).isBlank()) {
                    merged.put(k, v);
                }
            });
        }
        String pan = resolvePanNumber(app);
        if (!pan.isBlank()) {
            merged.put("panNumber", pan);
        }
        String requestPan = payload != null ? stringValue(payload, "panNumber") : "";
        if (!requestPan.isBlank()) {
            merged.put("panNumber", requestPan.trim().toUpperCase());
        }
        normalizeKycFieldAliases(merged);
        return merged;
    }

    /** Map intake field keys to the names expected by KYC providers. */
    private static void normalizeKycFieldAliases(Map<String, Object> merged) {
        String dl = firstNonBlank(merged, "dlNo", "drivingLicenseNumber", "dlNumber");
        if (!dl.isBlank()) {
            String upper = dl.toUpperCase();
            merged.put("dlNo", upper);
            merged.put("drivingLicenseNumber", upper);
            merged.put("dlNumber", upper);
        }
        String epic = firstNonBlank(merged, "epicNo", "voterId");
        if (!epic.isBlank()) {
            String upper = epic.toUpperCase();
            merged.put("epicNo", upper);
            merged.put("voterId", upper);
        }
        String dob = firstNonBlank(merged, "dob", "dateOfBirth", "drivingLicenseDob");
        if (!dob.isBlank()) {
            merged.putIfAbsent("dob", dob);
            merged.putIfAbsent("drivingLicenseDob", dob);
            merged.putIfAbsent("dateOfBirth", dob);
        }
        String acct = firstNonBlank(merged, "accountNumber", "bankAccountNumber");
        if (!acct.isBlank()) {
            merged.putIfAbsent("accountNumber", acct);
            merged.putIfAbsent("bankAccountNumber", acct);
        }
    }

    private static String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String v = stringValue(map, key);
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static boolean isAnchor(LoanApplication app) {
        return app.getIntakeSegment() == IntakeSegment.ANCHOR;
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key) && source.get(key) != null) {
                target.put(key, source.get(key));
            }
        }
    }

    private static String stringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return "";
        }
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }
}
