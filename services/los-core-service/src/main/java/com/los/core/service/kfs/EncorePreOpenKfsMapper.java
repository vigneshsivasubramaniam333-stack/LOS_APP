package com.los.core.service.kfs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.encore.client.support.EncoreRepaymentScheduleParser;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps Encore {@code findPreOpenSummary} JSON (stored on KFS charges) into KFS financial figures.
 */
@Slf4j
public final class EncorePreOpenKfsMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EncorePreOpenKfsMapper() {
    }

    public record Figures(
            BigDecimal installmentAmount,
            BigDecimal totalRepayment,
            BigDecimal totalInterest,
            BigDecimal apr,
            int installmentCount) {
    }

    public static Optional<Figures> fromCharges(Map<String, Object> charges, BigDecimal principal) {
        if (charges == null || principal == null) {
            return Optional.empty();
        }
        Object raw = charges.get("encorePreOpenSummaryJson");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(String.valueOf(raw));
            if (root.isArray() && !root.isEmpty()) {
                root = root.get(0);
            }
            if (!root.isObject()) {
                return Optional.empty();
            }

            List<Map<String, Object>> schedule = EncoreRepaymentScheduleParser.parseFromSummaryRoot(root);
            if (schedule.isEmpty()) {
                return Optional.empty();
            }

            BigDecimal installment = firstInstallment(schedule, root);
            BigDecimal totalRepayment = sumInstallments(schedule);
            BigDecimal totalInterest = totalRepayment.subtract(principal).max(BigDecimal.ZERO);
            BigDecimal apr = extractApr(root).orElse(null);

            return Optional.of(new Figures(
                    installment,
                    totalRepayment,
                    totalInterest,
                    apr,
                    schedule.size()));
        } catch (Exception e) {
            log.warn("Could not parse encorePreOpenSummaryJson for KFS: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static BigDecimal firstInstallment(List<Map<String, Object>> schedule, JsonNode root) {
        Object fromSchedule = schedule.get(0).get("installmentAmount");
        if (fromSchedule != null) {
            return toBigDecimal(fromSchedule);
        }
        for (String key : List.of("installmentAmount", "emiAmount", "nextInstallmentAmount")) {
            if (root.has(key) && !root.get(key).isNull()) {
                return toBigDecimal(root.get(key));
            }
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal sumInstallments(List<Map<String, Object>> schedule) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> row : schedule) {
            Object amt = row.get("installmentAmount");
            if (amt != null) {
                sum = sum.add(toBigDecimal(amt));
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static Optional<BigDecimal> extractApr(JsonNode root) {
        for (String key : List.of("apr", "annualPercentageRate", "annualPercentRate", "effectiveApr")) {
            if (root.has(key) && !root.get(key).isNull()) {
                return Optional.of(toBigDecimal(root.get(key)));
            }
        }
        return Optional.empty();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof JsonNode node) {
            if (node.isNumber()) {
                return BigDecimal.valueOf(node.asDouble()).setScale(2, RoundingMode.HALF_UP);
            }
            if (node.isTextual()) {
                return new BigDecimal(node.asText().trim()).setScale(2, RoundingMode.HALF_UP);
            }
            if (node.isObject() && node.has("magnitude")) {
                return BigDecimal.valueOf(node.get("magnitude").asDouble()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return new BigDecimal(String.valueOf(value).trim()).setScale(2, RoundingMode.HALF_UP);
    }
}
