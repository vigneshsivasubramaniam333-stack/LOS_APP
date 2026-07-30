package com.los.core.service.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code esignDocuments} from workflow ESIGN_AGREEMENT step options.
 */
public final class EsignDocumentsConfig {

    public record AdditionalDoc(String documentType, String label, boolean required) {}

    public record Settings(String defaultDocumentKey, List<AdditionalDoc> additional) {
        public static Settings singleDefault() {
            return new Settings("KFS_AGREEMENT", List.of());
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
    }

    private EsignDocumentsConfig() {}

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
            if (!"ESIGN_AGREEMENT".equalsIgnoreCase(name) && !"ESIGN".equalsIgnoreCase(name)) {
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
            List<AdditionalDoc> additional = new ArrayList<>();
            Object addRaw = map.get("additional");
            if (addRaw instanceof List<?> list) {
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
                    additional.add(new AdditionalDoc(type.toUpperCase(Locale.ROOT), label, required));
                }
            }
            return new Settings(defaultKey, Collections.unmodifiableList(additional));
        }
        return Settings.singleDefault();
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
