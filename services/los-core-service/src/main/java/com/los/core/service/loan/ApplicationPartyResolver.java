package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves primary party contact and identity fields for integrations (LMS, eSign, VKYC, bureau, KYC)
 * from either borrower {@code personalInfo} or anchor {@code businessInfo}, based on {@code intakeSegment}.
 */
public final class ApplicationPartyResolver {

    private ApplicationPartyResolver() {
    }

    public static boolean isAnchor(LoanApplication app) {
        return app != null && app.getIntakeSegment() == IntakeSegment.ANCHOR;
    }

    /** Display / customer name for LMS, notifications, and signing. */
    public static String resolveDisplayName(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (isAnchor(app)) {
            String anchor = firstNonBlank(
                    stringValue(app.getBusinessInfo(), "corporateName"),
                    stringValue(app.getBusinessInfo(), "businessName"),
                    stringValue(app.getBusinessInfo(), "accountHolderName"));
            if (!anchor.isBlank()) {
                return anchor;
            }
        }
        Map<String, Object> pi = app.getPersonalInfo();
        if (pi == null) {
            return isAnchor(app) ? "Anchor" : "";
        }
        String name = firstNonBlank(
                stringValue(pi, "fullName"),
                stringValue(pi, "name"));
        if (!name.isBlank()) {
            return name;
        }
        String first = stringValue(pi, "firstName");
        String last = stringValue(pi, "lastName");
        String combined = (first + " " + last).trim();
        return combined.isBlank() ? (isAnchor(app) ? "Anchor" : "") : combined;
    }

    /** Primary email for notifications, portal invite, and anchor master sync (corporate contact). */
    public static String resolveEmail(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (isAnchor(app)) {
            String fromBusiness = stringValue(app.getBusinessInfo(), "email");
            if (!fromBusiness.isBlank()) {
                return fromBusiness;
            }
        }
        Map<String, Object> pi = app.getPersonalInfo();
        if (pi == null) {
            pi = Map.of();
        }
        String fromPersonal = firstNonBlank(
                stringValue(pi, "borrowerEmail"),
                stringValue(pi, "email"),
                stringValue(pi, "contactEmail"));
        if (!fromPersonal.isBlank()) {
            return fromPersonal;
        }
        // Business invoice-discounting borrowers often store contact email on businessInfo.
        if (!isAnchor(app) && app.getBusinessInfo() != null) {
            return firstNonBlank(
                    stringValue(app.getBusinessInfo(), "email"),
                    stringValue(app.getBusinessInfo(), "contactEmail"));
        }
        return "";
    }

    public static String resolveMobile(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (isAnchor(app)) {
            String fromBusiness = stringValue(app.getBusinessInfo(), "mobile");
            if (!fromBusiness.isBlank()) {
                return fromBusiness;
            }
        }
        Map<String, Object> pi = app.getPersonalInfo();
        if (pi == null) {
            return "";
        }
        return firstNonBlank(
                stringValue(pi, "borrowerMobile"),
                stringValue(pi, "mobile"),
                stringValue(pi, "phone"));
    }

    /**
     * E-sign invite target: first signing authority (by signingOrder) when contacts are present;
     * otherwise corporate {@link #resolveEmail}.
     */
    public static String resolveEsignEmail(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (isAnchor(app)) {
            String fromSigning = firstSigningAuthorityField(app.getBusinessInfo(), "email");
            if (looksLikeEmail(fromSigning)) {
                return fromSigning;
            }
        }
        return resolveEmail(app);
    }

    /** E-sign signer mobile: signing authority when available, else corporate mobile. */
    public static String resolveEsignMobile(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (isAnchor(app)) {
            String fromSigning = firstSigningAuthorityField(app.getBusinessInfo(), "mobile");
            if (!fromSigning.isBlank() && fromSigning.replaceAll("\\D", "").length() >= 10) {
                return fromSigning;
            }
        }
        return resolveMobile(app);
    }

    /** E-sign signer display name: signing authority name when available, else corporate name. */
    public static String resolveEsignDisplayName(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (isAnchor(app)) {
            String fromSigning = firstSigningAuthorityField(app.getBusinessInfo(), "name");
            if (!fromSigning.isBlank()) {
                return fromSigning;
            }
        }
        return resolveDisplayName(app);
    }

    public static String resolvePincode(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (isAnchor(app)) {
            String fromBusiness = firstNonBlank(
                    stringValue(app.getBusinessInfo(), "pincode"),
                    stringValue(app.getBusinessInfo(), "pinCode"));
            if (!fromBusiness.isBlank()) {
                return digitsOnlyPin(fromBusiness);
            }
        }
        Map<String, Object> pi = app.getPersonalInfo();
        if (pi == null) {
            return "";
        }
        return digitsOnlyPin(firstNonBlank(
                stringValue(pi, "pincode"),
                stringValue(pi, "pinCode"),
                stringValue(pi, "postalCode")));
    }

    public static String resolveAddressLine(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (isAnchor(app)) {
            String fromBusiness = firstNonBlank(
                    stringValue(app.getBusinessInfo(), "addressLine"),
                    stringValue(app.getBusinessInfo(), "address"));
            if (!fromBusiness.isBlank()) {
                return fromBusiness;
            }
        }
        Map<String, Object> pi = app.getPersonalInfo();
        if (pi == null) {
            return "";
        }
        return firstNonBlank(
                stringValue(pi, "addressLine"),
                stringValue(pi, "addressLine1"),
                stringValue(pi, "address"));
    }

    /**
     * Merged party map for LMS handover / geo extraction (personal + business with canonical aliases).
     */
    public static Map<String, Object> buildIntegrationPartyMap(LoanApplication app) {
        Map<String, Object> out = new HashMap<>();
        if (app.getPersonalInfo() != null) {
            out.putAll(app.getPersonalInfo());
        }
        if (app.getBusinessInfo() != null) {
            out.putAll(app.getBusinessInfo());
        }
        String name = resolveDisplayName(app);
        if (!name.isBlank()) {
            out.putIfAbsent("fullName", name);
            out.putIfAbsent("name", name);
        }
        String email = resolveEmail(app);
        if (!email.isBlank()) {
            out.putIfAbsent("email", email);
            out.putIfAbsent("borrowerEmail", email);
        }
        String mobile = resolveMobile(app);
        if (!mobile.isBlank()) {
            out.putIfAbsent("mobile", mobile);
            out.putIfAbsent("phone", mobile);
            out.putIfAbsent("borrowerMobile", mobile);
        }
        String pin = resolvePincode(app);
        if (!pin.isBlank()) {
            out.put("pincode", pin);
            out.put("pinCode", pin);
        }
        String address = resolveAddressLine(app);
        if (!address.isBlank()) {
            out.putIfAbsent("addressLine", address);
            out.putIfAbsent("address", address);
        }
        String pan = ApplicantIdentityResolver.resolvePanNumber(app);
        if (!pan.isBlank()) {
            out.put("panNumber", pan);
        }
        if (app.getId() != null) {
            out.putIfAbsent("applicationId", app.getId().toString());
        }
        if (app.getApplicationNumber() != null) {
            out.putIfAbsent("applicationNumber", app.getApplicationNumber());
        }
        return out;
    }

    /** VKYC / Hyperverge provider payload with segment-aware field resolution. */
    public static Map<String, Object> buildVkycProviderPayload(LoanApplication app) {
        Map<String, Object> payload = new HashMap<>(buildIntegrationPartyMap(app));
        Map<String, Object> pi = app.getPersonalInfo();
        if (pi != null) {
            putIfPresent(payload, "dob", firstNonBlank(
                    stringValue(pi, "dateOfBirth"),
                    stringValue(pi, "dob"),
                    stringValue(pi, "drivingLicenseDob")));
            if (isAnchor(app)) {
                putIfPresent(payload, "dob", stringValue(app.getBusinessInfo(), "dateOfIncorporation"));
            }
        }
        return payload;
    }

    /**
     * Enriches eSign signer map from application when request omits email/name (anchor signing authority).
     */
    public static Map<String, Object> enrichEsignSignerInfo(LoanApplication app, Map<String, Object> incoming) {
        Map<String, Object> out = new HashMap<>(incoming != null ? incoming : Map.of());
        String email = resolveEsignEmail(app);
        if (!email.isBlank()) {
            if (isBlankValue(out.get("borrowerEmail"))) {
                out.put("borrowerEmail", email);
            }
            if (isBlankValue(out.get("email"))) {
                out.put("email", email);
            }
        }
        String name = resolveEsignDisplayName(app);
        if (!name.isBlank()) {
            if (isBlankValue(out.get("name"))) {
                out.put("name", name);
            }
            if (isBlankValue(out.get("borrowerName"))) {
                out.put("borrowerName", name);
            }
            if (isBlankValue(out.get("fullName"))) {
                out.put("fullName", name);
            }
        }
        String mobile = resolveEsignMobile(app);
        if (!mobile.isBlank() && isBlankValue(out.get("phone"))) {
            out.put("phone", mobile);
        }
        if (!mobile.isBlank() && isBlankValue(out.get("mobile"))) {
            out.put("mobile", mobile);
        }
        return out;
    }

    /** Enriches signer info from a specific application party (co-applicant or primary). */
    public static Map<String, Object> enrichEsignSignerInfoFromParty(
            com.los.core.model.entity.ApplicationParty party, Map<String, Object> incoming) {
        Map<String, Object> out = new HashMap<>(incoming != null ? incoming : Map.of());
        if (party == null || party.getPersonalInfo() == null) {
            return out;
        }
        Map<String, Object> info = party.getPersonalInfo();
        String email = firstNonBlank(stringValue(info, "email"), stringValue(info, "borrowerEmail"),
                stringValue(info, "contactEmail"));
        if (!email.isBlank()) {
            out.put("borrowerEmail", email);
            out.put("email", email);
        }
        String name = firstNonBlank(stringValue(info, "fullName"), stringValue(info, "name"),
                (stringValue(info, "firstName") + " " + stringValue(info, "lastName")).trim());
        if (!name.isBlank()) {
            out.put("name", name);
            out.put("borrowerName", name);
            out.put("fullName", name);
        }
        String mobile = firstNonBlank(stringValue(info, "mobile"), stringValue(info, "phone"));
        if (!mobile.isBlank()) {
            out.put("phone", mobile);
            out.put("mobile", mobile);
        }
        if (party.getId() != null) {
            out.put("partyId", party.getId().toString());
        }
        return out;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    private static boolean isBlankValue(Object v) {
        return v == null || String.valueOf(v).trim().isEmpty();
    }

    private static String digitsOnlyPin(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.length() >= 6 ? digits.substring(0, 6) : digits;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String stringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return "";
        }
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    /**
     * First signing authority from {@code businessInfo.contacts}, ordered by {@code signingOrder}
     * (order 1 is invited first for multi-signer setups).
     */
    @SuppressWarnings("unchecked")
    private static String firstSigningAuthorityField(Map<String, Object> businessInfo, String field) {
        if (businessInfo == null) {
            return "";
        }
        Object raw = businessInfo.get("contacts");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        List<Map<String, Object>> authorities = new ArrayList<>();
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> m)) {
                continue;
            }
            Object flag = m.get("isSigningAuthority");
            boolean signing = Boolean.TRUE.equals(flag)
                    || "true".equalsIgnoreCase(String.valueOf(flag == null ? "" : flag).trim());
            if (!signing) {
                continue;
            }
            authorities.add((Map<String, Object>) m);
        }
        if (authorities.isEmpty()) {
            return "";
        }
        authorities.sort(Comparator.comparingInt(c -> {
            Object order = c.get("signingOrder");
            if (order instanceof Number n) {
                return n.intValue() > 0 ? n.intValue() : Integer.MAX_VALUE;
            }
            try {
                int n = Integer.parseInt(String.valueOf(order == null ? "0" : order).trim());
                return n > 0 ? n : Integer.MAX_VALUE;
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }));
        return stringValue(authorities.get(0), field);
    }

    private static boolean looksLikeEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String e = raw.trim();
        int at = e.indexOf('@');
        return at > 0 && at < e.length() - 1 && e.indexOf('.', at) > at + 1;
    }
}
