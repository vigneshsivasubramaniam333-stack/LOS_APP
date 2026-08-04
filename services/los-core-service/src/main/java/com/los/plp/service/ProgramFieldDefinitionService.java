package com.los.plp.service;

import com.los.core.exception.BusinessRuleException;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.client.PlpIntegrationException;
import com.los.plp.model.dto.ProgramFieldDefinitionResponse;
import com.los.plp.model.entity.ProgramFieldDefinition;
import com.los.plp.repository.ProgramFieldDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads program field definitions from PLP admin (source of truth). Optional local seed is used only
 * as offline/fallback so LOS can still create programs if PLP is temporarily unreachable.
 * Configuration / CRUD happens exclusively on PLP admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramFieldDefinitionService {

    private static final Set<String> VALID_TYPES = Set.of("TEXT", "NUMBER", "DROPDOWN");

    private final PlpIntegrationClient plpIntegrationClient;
    private final ProgramFieldDefinitionRepository localFallbackRepository;

    /** Active definitions for a product — prefers live PLP catalog. */
    public List<ProgramFieldDefinitionResponse> list(String productType, boolean activeOnly) {
        try {
            List<ProgramFieldDefinitionResponse> fromPlp =
                    plpIntegrationClient.listProgramFieldDefinitions(productType, activeOnly);
            if (fromPlp != null && !fromPlp.isEmpty()) {
                return fromPlp;
            }
            if (fromPlp != null) {
                return fromPlp;
            }
        } catch (PlpIntegrationException e) {
            log.warn("PLP field definitions unavailable, using local seed if present: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("PLP field definitions fetch failed, using local seed if present: {}", e.getMessage());
        }
        return listLocalFallback(productType, activeOnly);
    }

    /** Validate values using PLP definitions when available; otherwise pass through non-blank values. */
    public Map<String, Object> validateAndNormalizeValues(String productType, Map<String, Object> raw) {
        List<ProgramFieldDefinitionResponse> defs = list(productType, true);
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> input = raw != null ? new LinkedHashMap<>(raw) : new LinkedHashMap<>();

        if (defs == null || defs.isEmpty()) {
            // No catalog (PLP down + no seed): keep submitted values; dual-write still applies known keys
            for (Map.Entry<String, Object> e : input.entrySet()) {
                if (!isBlankValue(e.getValue())) {
                    out.put(e.getKey(), e.getValue());
                }
            }
            return out;
        }

        Map<String, ProgramFieldDefinitionResponse> byKey = defs.stream()
                .filter(d -> d.getFieldKey() != null && !d.getFieldKey().isBlank())
                .collect(Collectors.toMap(
                        ProgramFieldDefinitionResponse::getFieldKey,
                        d -> d,
                        (a, b) -> a,
                        LinkedHashMap::new));

        for (ProgramFieldDefinitionResponse def : defs) {
            Object v = resolveInputForDefinition(def, input);
            boolean blank = isBlankValue(v);
            if (blank) {
                if (def.isRequired()) {
                    throw new BusinessRuleException(
                            def.getLabel() + " is required",
                            "PROGRAM_CUSTOM_FIELD_REQUIRED",
                            "PROGRAM_CREATE",
                            Map.of("fieldKey", def.getFieldKey()));
                }
                continue;
            }
            out.put(def.getFieldKey(), coerceValue(def, v));
        }

        // Preserve extras not in catalog (already-stored or free-form keys)
        for (Map.Entry<String, Object> e : input.entrySet()) {
            if (!out.containsKey(e.getKey())
                    && !byKey.containsKey(e.getKey())
                    && !isBlankValue(e.getValue())
                    && !isAliasConsumed(e.getKey(), byKey.keySet())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static boolean isAliasConsumed(String key, Set<String> defKeys) {
        // If definition uses PLP storage key, don't re-emit LOS dual-name scalar keys as extras
        if ("maxInvoiceVintageDays".equals(key) && defKeys.contains("maxInvoiceAgeDays")) {
            return true;
        }
        if ("tenureDays".equals(key) && defKeys.contains("maxTenureDays")) {
            return true;
        }
        if ("maxInvoiceAgeDays".equals(key) && defKeys.contains("maxInvoiceVintageDays")) {
            return true;
        }
        if ("maxTenureDays".equals(key) && defKeys.contains("tenureDays")) {
            return true;
        }
        return false;
    }

    /**
     * Prefer definition field_key; also accept LOS dual-name aliases for system fields so older
     * clients that post scalars still validate.
     */
    private static Object resolveInputForDefinition(
            ProgramFieldDefinitionResponse def, Map<String, Object> input) {
        String key = def.getFieldKey();
        Object v = input.get(key);
        if (!isBlankValue(v)) {
            return v;
        }
        if ("maxInvoiceAgeDays".equals(key)) {
            return input.get("maxInvoiceVintageDays");
        }
        if ("maxInvoiceVintageDays".equals(key)) {
            return input.get("maxInvoiceAgeDays");
        }
        if ("maxTenureDays".equals(key)) {
            return input.get("tenureDays");
        }
        if ("tenureDays".equals(key)) {
            return input.get("maxTenureDays");
        }
        return null;
    }

    private List<ProgramFieldDefinitionResponse> listLocalFallback(String productType, boolean activeOnly) {
        List<ProgramFieldDefinition> rows = activeOnly
                ? localFallbackRepository.findByActiveTrueOrderBySortOrderAscLabelAsc()
                : localFallbackRepository.findAllByOrderBySortOrderAscLabelAsc();
        if (productType != null && !productType.isBlank()) {
            rows = rows.stream().filter(d -> appliesToProduct(d, productType)).toList();
        }
        return rows.stream().map(this::toResponse).toList();
    }

    private Object coerceValue(ProgramFieldDefinitionResponse def, Object v) {
        String type = def.getInputType() != null ? def.getInputType().toUpperCase(Locale.ROOT) : "TEXT";
        if (!VALID_TYPES.contains(type)) {
            type = "TEXT";
        }
        return switch (type) {
            case "NUMBER" -> {
                if (v instanceof Number n) {
                    yield n.intValue() == n.doubleValue() ? n.intValue() : n.doubleValue();
                }
                String s = String.valueOf(v).trim();
                try {
                    if (s.contains(".")) {
                        yield Double.parseDouble(s);
                    }
                    yield Integer.parseInt(s);
                } catch (NumberFormatException ex) {
                    throw new BusinessRuleException(
                            def.getLabel() + " must be a number",
                            "PROGRAM_CUSTOM_FIELD_INVALID",
                            "PROGRAM_CREATE",
                            Map.of("fieldKey", def.getFieldKey()));
                }
            }
            case "DROPDOWN" -> {
                String s = String.valueOf(v).trim();
                List<Map<String, Object>> options = def.getOptions();
                if (options != null && !options.isEmpty()) {
                    boolean ok = options.stream().anyMatch(o ->
                            s.equalsIgnoreCase(String.valueOf(o.get("value"))));
                    if (!ok) {
                        throw new BusinessRuleException(
                                def.getLabel() + " has an invalid option",
                                "PROGRAM_CUSTOM_FIELD_INVALID",
                                "PROGRAM_CREATE",
                                Map.of("fieldKey", def.getFieldKey(), "value", s));
                    }
                }
                yield s.toUpperCase(Locale.ROOT);
            }
            default -> String.valueOf(v).trim();
        };
    }

    private static boolean isBlankValue(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    private static boolean appliesToProduct(ProgramFieldDefinition d, String productType) {
        List<String> types = d.getProductTypes();
        if (types == null || types.isEmpty()) {
            return true;
        }
        String p = productType.trim().toUpperCase(Locale.ROOT);
        return types.stream().anyMatch(t -> p.equalsIgnoreCase(String.valueOf(t)));
    }

    private ProgramFieldDefinitionResponse toResponse(ProgramFieldDefinition d) {
        return ProgramFieldDefinitionResponse.builder()
                .id(d.getId())
                .fieldKey(d.getFieldKey())
                .label(d.getLabel())
                .inputType(d.getInputType())
                .options(d.getOptionsJson() != null ? new ArrayList<>(d.getOptionsJson()) : null)
                .required(d.isRequired())
                .active(d.isActive())
                .sortOrder(d.getSortOrder())
                .productTypes(d.getProductTypes())
                .systemManaged(d.isSystemManaged())
                .helpText(d.getHelpText())
                .storageTarget(d.getStorageTarget())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
