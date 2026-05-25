package com.los.encore.client.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncoreRepaymentScheduleParserTest {

    /**
     * Golden-style fixture: first-object summary shape from Encore findSummaries / findSummary
     * (bl-core {@code findRepaymentSchedules} path).
     */
    private static final String SUMMARY_WITH_SCHEDULE = """
            {
              "accountId": "ACC-001",
              "repaymentSchedule": [
                {
                  "sequenceNum": 1,
                  "description": "Inst 1",
                  "amount1": "5000.00",
                  "amount2": "95000.00",
                  "amount3": "5000.00",
                  "valueDateStr": "2026-06-01",
                  "part1": "12.0",
                  "part2": "88.0",
                  "part3": "0.0"
                },
                {
                  "sequenceNum": 2,
                  "description": "Inst 2",
                  "amount1": "5000.00",
                  "amount2": "90000.00",
                  "amount3": "5000.00",
                  "valueDateStr": "2026-07-01",
                  "part1": "10.0",
                  "part2": "90.0",
                  "part3": "0.0"
                }
              ]
            }
            """;

    @Test
    void parseFromSummaryRoot_mapsInstallmentLines() throws Exception {
        JsonNode root = new ObjectMapper().readTree(SUMMARY_WITH_SCHEDULE);
        List<Map<String, Object>> rows = EncoreRepaymentScheduleParser.parseFromSummaryRoot(root);

        assertEquals(2, rows.size());
        Map<String, Object> first = rows.get(0);
        assertEquals(1, ((Number) first.get("sequenceNum")).intValue());
        assertEquals("Inst 1", first.get("description"));
        assertEquals("5000.00", first.get("installmentAmount"));
        assertEquals("5000.00", first.get("amountDue"));
        assertEquals("2026-06-01", first.get("valueDateStr"));
        assertEquals("FROM_ENCORE", first.get("status"));
        assertTrue(((Number) first.get("interestAmount")).doubleValue() > 0);
        assertTrue(((Number) first.get("principalAmount")).doubleValue() > 0);
    }

    @Test
    void parseFromSummaryRoot_emptyWhenNoSchedule() throws Exception {
        JsonNode root = new ObjectMapper().readTree("{\"accountId\":\"X\"}");
        assertTrue(EncoreRepaymentScheduleParser.parseFromSummaryRoot(root).isEmpty());
    }
}
