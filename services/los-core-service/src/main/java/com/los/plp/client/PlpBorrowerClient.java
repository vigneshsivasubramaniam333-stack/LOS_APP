package com.los.plp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.plp.config.PlpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads/writes borrower-scoped PLP resources (invoices, finance requests, invoice-discounting loans and
 * repayments) on behalf of a LOS borrower. Uses the shared PLP machine identity ({@code los.plp.integration-*},
 * a PLP lender role) so the gateway authorizes access to any {@code borrowerId}. The borrower is identified by
 * the PLP borrower UUID that LOS captured when the borrower's application was synced ({@code plp_borrower_id}).
 *
 * <p>All methods throw {@link PlpIntegrationException} on transport/HTTP/parse failures so the calling service
 * can degrade gracefully (PLP off / unreachable → empty invoice-discounting view).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlpBorrowerClient {

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    @Qualifier("plpRestClient")
    private final RestClient plpRestClient;
    private final PlpProperties plpProperties;
    private final PlpIntegrationClient plpIntegrationClient;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return plpProperties.isEnabled();
    }

    /** All invoices visible to a borrower. PLP returns a raw JSON array (not wrapped in {status,data}). */
    public List<Map<String, Object>> listInvoices(UUID plpBorrowerId) {
        String raw = get("/api/v1/invoices/borrower/" + plpBorrowerId);
        return parseList(raw);
    }

    /** All loans for a borrower (lender scope passes borrowerId as a query filter). PLP wraps in {status,data}. */
    public List<Map<String, Object>> listLoans(UUID plpBorrowerId) {
        String raw = get("/api/v1/loans?borrowerId=" + plpBorrowerId);
        return parseDataList(raw);
    }

    /** Purchase-flow: borrower accepts an anchor-eligible invoice before requesting finance. */
    public Map<String, Object> acceptInvoice(UUID plpBorrowerId, UUID invoiceId) {
        String raw = post("/api/v1/invoices/" + invoiceId + "/borrower-accept?borrowerId=" + plpBorrowerId, Map.of());
        return parseInvoice(raw);
    }

    /** Request invoice-discounting finance against a single invoice; returns the created PLP loan. */
    public Map<String, Object> requestFinance(UUID plpBorrowerId, UUID invoiceId, BigDecimal amount, UUID programId) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("borrowerId", plpBorrowerId.toString());
        body.put("invoiceId", invoiceId.toString());
        body.put("productType", "INVOICE_DISCOUNTING");
        body.put("requestedAmount", amount);
        if (programId != null) {
            body.put("programId", programId.toString());
        }
        String raw = post("/api/v1/loans", body);
        return parseData(raw);
    }

    /** Repayment history for a loan (newest first). */
    public List<Map<String, Object>> listLoanRepayments(UUID plpLoanId) {
        String raw = get("/api/v1/loans/" + plpLoanId + "/repayments");
        return parseDataList(raw);
    }

    /** Record a repayment against an invoice-discounting loan; returns the updated PLP loan. */
    public Map<String, Object> repay(UUID plpLoanId, BigDecimal amount) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        String raw = post("/api/v1/loans/" + plpLoanId + "/repay", body);
        return parseData(raw);
    }

    /** All sub-programs (lender machine identity). */
    public List<Map<String, Object>> listSubPrograms() {
        String raw = get("/api/v1/sub-programs");
        return parseDataList(raw);
    }

    /** Program detail including aggregate limits ({@code GET /api/v1/programs/{id}}). */
    public Map<String, Object> getProgram(UUID programId) {
        String raw = get("/api/v1/programs/" + programId);
        return parseData(raw);
    }

    /**
     * Borrower membership limits for a sub-program, or empty when the borrower is not enrolled.
     */
    public Optional<Map<String, Object>> getBorrowerLimitSummary(UUID subProgramId, UUID borrowerId) {
        try {
            String raw = get("/api/v1/sub-programs/" + subProgramId + "/borrowers/" + borrowerId + "/limit-summary");
            Map<String, Object> data = parseData(raw);
            return data.isEmpty() ? Optional.empty() : Optional.of(data);
        } catch (PlpIntegrationException e) {
            if (e.getMessage() != null && e.getMessage().contains("HTTP 403")) {
                return Optional.empty();
            }
            if (e.getMessage() != null && e.getMessage().contains("HTTP 404")) {
                return Optional.empty();
            }
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not enrolled")) {
                return Optional.empty();
            }
            throw e;
        }
    }

    // ----- transport -----

    private String get(String path) {
        String bearer = "Bearer " + plpIntegrationClient.currentBearerToken();
        try {
            return plpRestClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("[PLP][borrower] GET {} -> HTTP {} {}", path, e.getStatusCode(),
                    truncate(e.getResponseBodyAsString()));
            throw new PlpIntegrationException("PLP HTTP " + e.getStatusCode() + " on " + path);
        } catch (PlpIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PLP][borrower] GET {} failed: {}", path, e.getMessage());
            throw new PlpIntegrationException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private String post(String path, Object body) {
        String bearer = "Bearer " + plpIntegrationClient.currentBearerToken();
        try {
            return plpRestClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("[PLP][borrower] POST {} -> HTTP {} {}", path, e.getStatusCode(),
                    truncate(e.getResponseBodyAsString()));
            throw new PlpIntegrationException("PLP HTTP " + e.getStatusCode() + ": " + safeMessage(e.getResponseBodyAsString()));
        } catch (PlpIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PLP][borrower] POST {} failed: {}", path, e.getMessage());
            throw new PlpIntegrationException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private List<Map<String, Object>> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, LIST_OF_MAPS);
        } catch (Exception e) {
            throw new PlpIntegrationException("PLP: failed to parse invoice list: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDataList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> wrapper = objectMapper.readValue(raw, MAP);
            Object data = wrapper.get("data");
            if (data instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
            return List.of();
        } catch (Exception e) {
            throw new PlpIntegrationException("PLP: failed to parse loan list: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseData(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> wrapper = objectMapper.readValue(raw, MAP);
            Object data = wrapper.get("data");
            if (data instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return wrapper;
        } catch (Exception e) {
            throw new PlpIntegrationException("PLP: failed to parse response: " + e.getMessage());
        }
    }

    /** Invoice endpoints return the entity directly (not wrapped in {status,data}). */
    private Map<String, Object> parseInvoice(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, MAP);
        } catch (Exception e) {
            throw new PlpIntegrationException("PLP: failed to parse invoice response: " + e.getMessage());
        }
    }

    private static String safeMessage(String body) {
        if (body == null || body.isBlank()) {
            return "request rejected";
        }
        return truncate(body);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 512 ? s : s.substring(0, 512) + "...";
    }
}
