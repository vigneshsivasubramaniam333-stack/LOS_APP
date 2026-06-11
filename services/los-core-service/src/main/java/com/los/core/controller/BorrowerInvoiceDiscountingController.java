package com.los.core.controller;

import com.los.core.model.dto.request.BorrowerFinanceRequest;
import com.los.core.model.dto.request.BorrowerRepaymentRequest;
import com.los.core.model.dto.response.BorrowerInvoiceRepaymentItemResponse;
import com.los.core.model.dto.response.BorrowerInvoiceDiscountingResponse;
import com.los.core.service.borrower.BorrowerInvoiceDiscountingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Borrower Invoice discounting: list invoices, request finance, and repay invoice-discounting loans.
 * Backed by PLP (proxied server-side); degrades gracefully when PLP is unavailable.
 */
@RestController
@RequestMapping("/api/v1/borrower/invoice-discounting")
@RequiredArgsConstructor
@Tag(name = "Borrower invoice discounting", description = "List invoices, request finance, and repay (PLP-backed)")
public class BorrowerInvoiceDiscountingController {

    private final BorrowerInvoiceDiscountingService service;

    @GetMapping
    @Operation(summary = "Borrower invoices and invoice-discounting loans")
    public ResponseEntity<BorrowerInvoiceDiscountingResponse> overview(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.overview(uid));
    }

    @PostMapping("/invoices/{invoiceId}/accept")
    @Operation(summary = "Accept an eligible purchase-flow invoice before requesting finance")
    public ResponseEntity<BorrowerInvoiceDiscountingResponse> acceptInvoice(
            @PathVariable UUID invoiceId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.acceptInvoice(uid, invoiceId));
    }

    @PostMapping("/invoices/{invoiceId}/finance")
    @Operation(summary = "Request finance against an eligible invoice")
    public ResponseEntity<BorrowerInvoiceDiscountingResponse> requestFinance(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody BorrowerFinanceRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.requestFinance(uid, invoiceId, request.getAmount()));
    }

    @PostMapping("/loans/{loanId}/repay")
    @Operation(summary = "Repay an invoice-discounting loan")
    public ResponseEntity<BorrowerInvoiceDiscountingResponse> repay(
            @PathVariable UUID loanId,
            @Valid @RequestBody BorrowerRepaymentRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.repayLoan(uid, loanId, request.getAmount()));
    }

    @GetMapping("/loans/{loanId}/repayments")
    @Operation(summary = "Repayment history for an invoice-discounting loan")
    public ResponseEntity<java.util.List<BorrowerInvoiceRepaymentItemResponse>> repayments(
            @PathVariable UUID loanId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        service.requireBorrower(role);
        return ResponseEntity.ok(service.listRepayments(uid, loanId));
    }
}
