package com.los.encore.client.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses Encore {@code repaymentSchedule} arrays from a loan summary JSON object (bl-core
 * {@code EncoreServiceFacadeImpl#findRepaymentSchedules} shape).
 */
public final class EncoreRepaymentScheduleParser {

    private EncoreRepaymentScheduleParser() {
    }

    /**
     * @param summaryRoot first element returned by findSummaries for a single account (bl-core {@code findSummary})
     */
    public static List<Map<String, Object>> parseFromSummaryRoot(JsonNode summaryRoot) {
        List<Map<String, Object>> schedules = new ArrayList<>();
        if (summaryRoot == null || !summaryRoot.isObject()) {
            return schedules;
        }
        JsonNode repaymentSchedule = summaryRoot.path("repaymentSchedule");
        if (!repaymentSchedule.isArray()) {
            return schedules;
        }
        for (JsonNode entry : repaymentSchedule) {
            Map<String, Object> scheduleEntry = new LinkedHashMap<>();
            scheduleEntry.put("sequenceNum", entry.path("sequenceNum").asInt(0));
            scheduleEntry.put("description", entry.path("description").asText(""));
            scheduleEntry.put("installmentAmount", entry.path("amount1").asText("0"));
            scheduleEntry.put("amountDue", entry.path("amount3").asText("0"));
            scheduleEntry.put("valueDateStr", entry.path("valueDateStr").asText(""));
            scheduleEntry.put("normalInterestRate", entry.path("part1").asDouble(0));
            scheduleEntry.put("principalRate", entry.path("part2").asDouble(0));
            scheduleEntry.put("penalInterestRate", entry.path("part3").asDouble(0));
            scheduleEntry.put("balance", entry.path("amount2").asText("0"));
            double installment = entry.path("amount1").asDouble(0);
            double interestRatePct = entry.path("part1").asDouble(0);
            double principalRatePct = entry.path("part2").asDouble(0);
            double totalRatePct = interestRatePct + principalRatePct;
            if (totalRatePct > 0 && installment > 0) {
                scheduleEntry.put("interestAmount", installment * interestRatePct / totalRatePct);
                scheduleEntry.put("principalAmount", installment * principalRatePct / totalRatePct);
            } else {
                scheduleEntry.put("interestAmount", 0.0);
                scheduleEntry.put("principalAmount", installment);
            }
            scheduleEntry.put("status", "FROM_ENCORE");
            schedules.add(scheduleEntry);
        }
        return schedules;
    }
}
