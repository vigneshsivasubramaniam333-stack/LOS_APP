package com.los.core.service.esign;

import com.los.core.model.entity.WorkflowConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses multi-document eSign config from workflow intake + optional ESIGN step legacy fields.
 * <ul>
 *   <li>{@code intakeConfig.esignSigningDocuments} — user-uploaded additional signing PDFs</li>
 *   <li>{@code intakeConfig.esignSystemDocuments} — system-generated signing PDFs (KFS / program terms /
 *       optional sanction letter). Templates may be stored for future use; generation still uses
 *       built-in procedures when templates are present.</li>
 *   <li>Legacy {@code esignDocuments} on the ESIGN workflow step — additional uploads + default key</li>
 * </ul>
 * Absent {@code esignSystemDocuments} preserves historical behaviour: only the default KFS /
 * program-terms document is signed unless uploaded additionals are configured.
 */
public final class EsignDocumentsConfig {

    public static final int DEFAULT_EXPECTED_PAGE_COUNT = 2;

    /** Document keys resolved from the KFS / program-terms generation path (not an upload). */
    public static final Set<String> PRIMARY_GENERATED_DOCUMENT_KEYS = Set.of(
            "KFS_AGREEMENT",
            "ANCHOR_PROGRAM_TERMS");

    /** Known system-generated document types (extensible; unknown keys ignored at materialize). */
    public static final String DOCUMENT_KEY_SANCTION_LETTER = "SANCTION_LETTER";

    public record AdditionalDoc(
            String documentType,
            String label,
            boolean required,
            boolean collectAtIntake,
            Integer expectedPageCount) {}

    /**
     * System auto-generated document to include in multi-doc eSign.
     * {@code templateBase64} is stored for future template-driven generation only.
     */
    public record SystemDoc(
            String documentKey,
            String label,
            boolean enabled,
            Integer expectedPageCount,
            String templateFileName,
            String templateMimeType,
            String templateBase64) {

        public boolean isPrimaryGenerated() {
            return documentKey != null
                    && PRIMARY_GENERATED_DOCUMENT_KEYS.contains(documentKey.trim().toUpperCase(Locale.ROOT));
        }
    }

    public record Settings(
            String defaultDocumentKey,
            List<AdditionalDoc> additional,
            Integer expectedPageCount,
            List<SystemDoc> systemDocuments) {
        public static Settings singleDefault() {
            return new Settings(
                    "KFS_AGREEMENT",
                    List.of(),
                    DEFAULT_EXPECTED_PAGE_COUNT,
                    List.of());
        }

        public List<String> requiredAdditionalTypes() {
            List<String> out = new ArrayList<>();
            for (AdditionalDoc d : additional) {
                if (d.required() && d.documentType() != null && !d.documentType().isBlank()) {
                    out.add(d.documentType().trim());
                }
            }
            return out;
        }

        public List<AdditionalDoc> intakeCollectAdditional() {
            List<AdditionalDoc> out = new ArrayList<>();
            for (AdditionalDoc d : additional) {
                if (d.collectAtIntake() && d.documentType() != null && !d.documentType().isBlank()) {
                    out.add(d);
                }
            }
            return out;
        }

        /** True when {@code documentKey} is configured as an additional signing document. */
        public boolean isAdditionalSigningType(String documentKey) {
            if (documentKey == null || documentKey.isBlank()) {
                return false;
            }
            String key = documentKey.trim().toUpperCase(Locale.ROOT);
            for (AdditionalDoc d : additional) {
                if (d.documentType() != null && key.equals(d.documentType().toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }

        /** Enabled system docs that are not the primary KFS/program-terms key. */
        public List<SystemDoc> enabledSystemExtras() {
            List<SystemDoc> out = new ArrayList<>();
            for (SystemDoc d : systemDocuments) {
                if (d == null || !d.enabled() || d.documentKey() == null || d.documentKey().isBlank()) {
                    continue;
                }
                if (d.isPrimaryGenerated()) {
                    continue;
                }
                out.add(d);
            }
            return out;
        }

        public boolean hasMultiDocumentSigning() {
            return !additional.isEmpty() || !enabledSystemExtras().isEmpty();
        }

        public int expectedPageCountFor(String documentKey) {
            if (documentKey != null) {
                String key = documentKey.trim().toUpperCase(Locale.ROOT);
                for (SystemDoc d : systemDocuments) {
                    if (d.documentKey() != null && key.equals(d.documentKey().toUpperCase(Locale.ROOT))
                            && d.expectedPageCount() != null && d.expectedPageCount() > 0) {
                        return d.expectedPageCount();
                    }
                }
                for (AdditionalDoc d : additional) {
                    if (d.documentType() != null && key.equals(d.documentType().toUpperCase(Locale.ROOT))
                            && d.expectedPageCount() != null && d.expectedPageCount() > 0) {
                        return d.expectedPageCount();
                    }
                }
            }
            if (expectedPageCount != null && expectedPageCount > 0) {
                return expectedPageCount;
            }
            return DEFAULT_EXPECTED_PAGE_COUNT;
        }

        public String labelFor(String documentKey) {
            if (documentKey == null || documentKey.isBlank()) {
                return "Agreement";
            }
            String key = documentKey.trim().toUpperCase(Locale.ROOT);
            for (SystemDoc d : systemDocuments) {
                if (d.documentKey() != null && key.equals(d.documentKey().toUpperCase(Locale.ROOT))) {
                    if (d.label() != null && !d.label().isBlank()) {
                        return d.label();
                    }
                    break;
                }
            }
            if ("KFS_AGREEMENT".equals(key)) {
                return "Key Fact Statement";
            }
            if ("ANCHOR_PROGRAM_TERMS".equals(key)) {
                return "Anchor program terms";
            }
            if (DOCUMENT_KEY_SANCTION_LETTER.equals(key)) {
                return "Sanction letter";
            }
            for (AdditionalDoc d : additional) {
                if (d.documentType() != null && key.equals(d.documentType().toUpperCase(Locale.ROOT))) {
                    return d.label() != null && !d.label().isBlank() ? d.label() : d.documentType();
                }
            }
            return documentKey.replace('_', ' ');
        }
    }

    private EsignDocumentsConfig() {}

    /**
     * Preferred entry: intake rules first, legacy ESIGN step config as fallback for additionals.
     * Default document key remains the generated KFS/program-terms path (not an intake upload).
     */
    public static Settings fromWorkflow(WorkflowConfig workflow) {
        if (workflow == null) {
            return Settings.singleDefault();
        }
        Settings stepSettings = fromWorkflowSteps(workflow.getSteps());
        Map<String, Object> intakeConfig = workflow.getIntakeConfig();
        List<SystemDoc> systemDocs = List.of();
        if (intakeConfig != null && intakeConfig.containsKey("esignSystemDocuments")) {
            systemDocs = parseSystemList(intakeConfig.get("esignSystemDocuments"));
        }
        String defaultKey = resolveDefaultDocumentKey(stepSettings.defaultDocumentKey(), systemDocs);
        // Prefer intake rules whenever the key is present (even empty — admin clears additionals).
        if (intakeConfig != null && intakeConfig.containsKey("esignSigningDocuments")) {
            List<AdditionalDoc> fromIntake = parseAdditionalList(intakeConfig.get("esignSigningDocuments"));
            return new Settings(
                    defaultKey,
                    Collections.unmodifiableList(fromIntake),
                    stepSettings.expectedPageCount(),
                    Collections.unmodifiableList(systemDocs));
        }
        return new Settings(
                defaultKey,
                stepSettings.additional(),
                stepSettings.expectedPageCount(),
                Collections.unmodifiableList(systemDocs));
    }

    /**
     * Prefer first enabled primary system document key when configured; otherwise step/default.
     */
    private static String resolveDefaultDocumentKey(String stepDefault, List<SystemDoc> systemDocs) {
        if (systemDocs != null) {
            for (SystemDoc d : systemDocs) {
                if (d != null && d.enabled() && d.isPrimaryGenerated() && d.documentKey() != null) {
                    return d.documentKey().trim().toUpperCase(Locale.ROOT);
                }
            }
        }
        if (stepDefault != null && !stepDefault.isBlank()) {
            return stepDefault.trim().toUpperCase(Locale.ROOT);
        }
        return "KFS_AGREEMENT";
    }

    @SuppressWarnings("unchecked")
    public static Settings fromWorkflowSteps(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return Settings.singleDefault();
        }
        for (Map<String, Object> step : steps) {
            if (step == null) {
                continue;
            }
            String name = String.valueOf(step.getOrDefault("step", step.getOrDefault("name", ""))).trim();
            if (!"ESIGN_AGREEMENT".equalsIgnoreCase(name)
                    && !"ESIGN".equalsIgnoreCase(name)
                    && !"ESIGN_KFS".equalsIgnoreCase(name)) {
                continue;
            }
            Object raw = step.get("esignDocuments");
            if (raw == null && step.get("extra") instanceof Map<?, ?> extra) {
                raw = ((Map<String, Object>) extra).get("esignDocuments");
            }
            if (!(raw instanceof Map<?, ?> map)) {
                return Settings.singleDefault();
            }
            Object defaultKeyRaw = map.get("defaultDocumentKey");
            String defaultKey = defaultKeyRaw != null
                    ? String.valueOf(defaultKeyRaw).trim()
                    : "KFS_AGREEMENT";
            if (defaultKey.isBlank()) {
                defaultKey = "KFS_AGREEMENT";
            }
            Integer topPageCount = parsePositiveInt(map.get("expectedPageCount"), DEFAULT_EXPECTED_PAGE_COUNT);
            List<AdditionalDoc> additional = parseAdditionalList(map.get("additional"));
            return new Settings(
                    defaultKey,
                    Collections.unmodifiableList(additional),
                    topPageCount,
                    List.of());
        }
        return Settings.singleDefault();
    }

    private static List<SystemDoc> parseSystemList(Object raw) {
        List<SystemDoc> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            String key = str(m.get("documentKey"));
            if (key.isBlank()) {
                key = str(m.get("documentType"));
            }
            if (key.isBlank()) {
                continue;
            }
            String documentKey = key.toUpperCase(Locale.ROOT);
            String label = str(m.get("label"));
            if (label.isBlank()) {
                label = defaultSystemLabel(documentKey);
            }
            boolean enabled = true;
            Object en = m.get("enabled");
            if (en instanceof Boolean b) {
                enabled = b;
            } else if (en != null) {
                enabled = !"false".equalsIgnoreCase(String.valueOf(en));
            }
            Integer pages = parsePositiveInt(m.get("expectedPageCount"), DEFAULT_EXPECTED_PAGE_COUNT);
            String templateFileName = blankToNull(str(m.get("templateFileName")));
            String templateMimeType = blankToNull(str(m.get("templateMimeType")));
            String templateBase64 = blankToNull(str(m.get("templateBase64")));
            out.add(new SystemDoc(
                    documentKey,
                    label,
                    enabled,
                    pages,
                    templateFileName,
                    templateMimeType,
                    templateBase64));
        }
        return out;
    }

    private static String defaultSystemLabel(String documentKey) {
        return switch (documentKey) {
            case "KFS_AGREEMENT" -> "Key Fact Statement";
            case "ANCHOR_PROGRAM_TERMS" -> "Anchor program terms";
            case DOCUMENT_KEY_SANCTION_LETTER -> "Sanction letter";
            default -> documentKey.replace('_', ' ');
        };
    }

    private static List<AdditionalDoc> parseAdditionalList(Object addRaw) {
        List<AdditionalDoc> additional = new ArrayList<>();
        if (!(addRaw instanceof List<?> list)) {
            return additional;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            String type = str(m.get("documentType"));
            if (type.isBlank()) {
                type = str(m.get("documentKey"));
            }
            if (type.isBlank()) {
                continue;
            }
            // Never treat generated primary / system sanction types as intake upload rows.
            String typeKey = type.toUpperCase(Locale.ROOT);
            if (PRIMARY_GENERATED_DOCUMENT_KEYS.contains(typeKey)
                    || DOCUMENT_KEY_SANCTION_LETTER.equals(typeKey)) {
                continue;
            }
            String label = str(m.get("label"));
            if (label.isBlank()) {
                label = type;
            }
            boolean required = true;
            Object req = m.get("required");
            if (req instanceof Boolean b) {
                required = b;
            } else if (req != null) {
                required = !"false".equalsIgnoreCase(String.valueOf(req));
            }
            // Default collect at intake to true when required (product mandate).
            boolean collectAtIntake = required;
            Object collectRaw = m.get("collectAtIntake");
            if (collectRaw instanceof Boolean cb) {
                collectAtIntake = cb;
            } else if (collectRaw != null) {
                collectAtIntake = !"false".equalsIgnoreCase(String.valueOf(collectRaw));
            }
            Integer docPageCount = parsePositiveInt(m.get("expectedPageCount"), DEFAULT_EXPECTED_PAGE_COUNT);
            additional.add(new AdditionalDoc(
                    typeKey, label, required, collectAtIntake, docPageCount));
        }
        return additional;
    }

    private static Integer parsePositiveInt(Object raw, Integer fallback) {
        if (raw instanceof Number n) {
            int v = n.intValue();
            return v > 0 ? v : fallback;
        }
        if (raw != null) {
            try {
                int v = Integer.parseInt(String.valueOf(raw).trim());
                return v > 0 ? v : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
