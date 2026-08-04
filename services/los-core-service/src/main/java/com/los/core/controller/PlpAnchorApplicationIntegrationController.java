package com.los.core.controller;

import com.los.core.exception.UnauthorizedException;
import com.los.core.model.dto.request.UpdateApplicationRequest;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.anchor.AnchorIntakeDelegationService;
import com.los.core.service.document.IDocumentService;
import com.los.core.service.loan.ILoanApplicationService;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound integration surface for the PLP anchor portal. PLP's program-service calls these
 * endpoints (server-to-server, no end-user JWT) to load/save the delegated anchor's LOS loan
 * application and to submit/resubmit anchor onboarding intake from the PLP portal.
 *
 * <p>Authenticated with a shared secret header (not the staff {@code X-User-Id}/{@code X-User-Role}
 * headers used elsewhere) since the caller is a trusted backend service, not an LOS staff user.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/integrations/plp/anchor-applications")
@RequiredArgsConstructor
@Tag(name = "PLP Anchor Integration", description = "Server-to-server endpoints for the PLP anchor onboarding portal")
public class PlpAnchorApplicationIntegrationController {

    public static final String INTEGRATION_KEY_HEADER = "X-Plp-Integration-Key";

    private final ILoanApplicationService loanApplicationService;
    private final AnchorIntakeDelegationService anchorIntakeDelegationService;
    private final IDocumentService documentService;
    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final LoanApplicationRepository loanApplicationRepository;

    @Value("${los.plp.inbound-api-key:}")
    private String configuredApiKey;

    @GetMapping("/{applicationId}")
    @Operation(summary = "Load the anchor's LOS application for the PLP onboarding portal")
    public ResponseEntity<ApplicationResponse> getApplication(
            @PathVariable UUID applicationId,
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKey) {
        requireIntegrationKey(integrationKey);
        return ResponseEntity.ok(loanApplicationService.getApplication(applicationId));
    }

    @PutMapping("/{applicationId}")
    @Operation(summary = "Save anchor intake section updates from the PLP onboarding portal")
    public ResponseEntity<ApplicationResponse> updateApplication(
            @PathVariable UUID applicationId,
            @RequestBody UpdateApplicationRequest request,
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKey) {
        requireIntegrationKey(integrationKey);
        rejectPersonalUpdateDuringDocVerificationSendBack(applicationId, request);
        return ResponseEntity.ok(loanApplicationService.updateApplication(applicationId, request));
    }

    @PostMapping("/{applicationId}/submit")
    @Operation(summary = "Anchor submits onboarding intake from the PLP portal")
    public ResponseEntity<ApplicationResponse> submit(
            @PathVariable UUID applicationId,
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKey) {
        requireIntegrationKey(integrationKey);
        anchorIntakeDelegationService.submitAnchorIntake(applicationId);
        return ResponseEntity.ok(loanApplicationService.getApplication(applicationId));
    }

    @PostMapping("/{applicationId}/resubmit")
    @Operation(summary = "Anchor resubmits onboarding intake after a send-back, from the PLP portal")
    public ResponseEntity<ApplicationResponse> resubmit(
            @PathVariable UUID applicationId,
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKey) {
        requireIntegrationKey(integrationKey);
        anchorIntakeDelegationService.resubmitAnchorIntake(applicationId);
        return ResponseEntity.ok(loanApplicationService.getApplication(applicationId));
    }

    @GetMapping("/{applicationId}/documents")
    @Operation(summary = "List documents for the delegated anchor application (PLP portal)")
    public ResponseEntity<List<DocumentResponse>> listDocuments(
            @PathVariable UUID applicationId,
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKey) {
        requireIntegrationKey(integrationKey);
        // Ensure application exists / is readable under the same auth gate as getApplication.
        loanApplicationService.getApplication(applicationId);
        return ResponseEntity.ok(documentService.getDocuments(applicationId));
    }

    @GetMapping("/{applicationId}/intake-context")
    @Operation(summary = "Application + active workflow intake config for the PLP portal wizard")
    public ResponseEntity<Map<String, Object>> intakeContext(
            @PathVariable UUID applicationId,
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKey) {
        requireIntegrationKey(integrationKey);
        ApplicationResponse application = loanApplicationService.getApplication(applicationId);
        LoanApplication entity = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", application);
        body.put("workflow", activeWorkflowConfigService.findActiveForApplication(entity)
                .map(this::toPortalWorkflowSnapshot)
                .orElse(null));
        return ResponseEntity.ok(body);
    }

    @PostMapping(value = "/{applicationId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document for the delegated anchor application (PLP portal)")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @PathVariable UUID applicationId,
            @RequestParam String documentType,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKey) {
        requireIntegrationKey(integrationKey);
        loanApplicationService.getApplication(applicationId);
        return ResponseEntity.ok(documentService.uploadDocument(applicationId, documentType, file, null));
    }

    @GetMapping("/{applicationId}/documents/{documentId}/download")
    @Operation(summary = "Download an anchor onboarding document for the delegated application")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID applicationId,
            @PathVariable UUID documentId,
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKey) {
        requireIntegrationKey(integrationKey);
        DocumentResponse doc = requireApplicationDocument(applicationId, documentId);
        byte[] data = documentService.downloadDocument(documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(doc.getFileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(resolveMediaType(doc.getContentType()))
                .body(data);
    }

    @GetMapping("/{applicationId}/documents/{documentId}/content")
    @Operation(summary = "Preview an anchor onboarding document for the delegated application")
    public ResponseEntity<byte[]> previewDocument(
            @PathVariable UUID applicationId,
            @PathVariable UUID documentId,
            @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKey) {
        requireIntegrationKey(integrationKey);
        DocumentResponse doc = requireApplicationDocument(applicationId, documentId);
        byte[] data = documentService.downloadDocument(documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(doc.getFileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(resolveMediaType(doc.getContentType()))
                .body(data);
    }

    private DocumentResponse requireApplicationDocument(UUID applicationId, UUID documentId) {
        loanApplicationService.getApplication(applicationId);
        return documentService.getDocuments(applicationId).stream()
                .filter(doc -> documentId.equals(doc.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document " + documentId + " is not linked to application " + applicationId));
    }

    private static MediaType resolveMediaType(String raw) {
        if (raw == null || raw.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(raw);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private Map<String, Object> toPortalWorkflowSnapshot(WorkflowConfig config) {
        Map<String, Object> wf = new LinkedHashMap<>();
        wf.put("id", config.getId());
        wf.put("name", config.getName());
        wf.put("borrowerType", config.getBorrowerType());
        wf.put("loanProduct", config.getLoanProduct());
        wf.put("lmsProductCode", config.getLmsProductCode());
        wf.put("lmsTenureUnit", config.getLmsTenureUnit());
        wf.put("intakeSegment", config.getIntakeSegment());
        wf.put("intakeIdentitySchema", config.getIntakeIdentitySchema());
        wf.put("intakeConfig", config.getIntakeConfig());
        wf.put("steps", config.getSteps());
        wf.put("active", config.isActive());
        return wf;
    }

    /**
     * During document-verification send-back, the anchor may only re-upload documents and resubmit.
     * Reject personal / business / financial / product mutation payloads from the portal.
     */
    private void rejectPersonalUpdateDuringDocVerificationSendBack(
            UUID applicationId, UpdateApplicationRequest request) {
        ApplicationResponse existing = loanApplicationService.getApplication(applicationId);
        if (existing.getStatus() != ApplicationStatus.DOC_VERIFICATION_SENT_BACK) {
            return;
        }
        if (request == null) {
            return;
        }
        boolean hasMutation =
                request.getPersonalInfo() != null
                        || request.getBusinessInfo() != null
                        || request.getFinancialInfo() != null
                        || request.getCollateralInfo() != null
                        || request.getRequestedAmount() != null
                        || request.getTenureMonths() != null
                        || (request.getLmsProductCode() != null && !request.getLmsProductCode().isBlank())
                        || (request.getLmsTenureUnit() != null && !request.getLmsTenureUnit().isBlank())
                        || (request.getRemarks() != null && !request.getRemarks().isBlank());
        if (hasMutation) {
            throw new IllegalArgumentException(
                    "Only document upload is allowed while document verification is sent back. "
                            + "Personal and KYC details cannot be changed.");
        }
    }

    private void requireIntegrationKey(String providedKey) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            log.warn("los.plp.inbound-api-key is not configured — rejecting PLP anchor integration call");
            throw new UnauthorizedException("PLP anchor integration is not configured");
        }
        if (providedKey == null || !configuredApiKey.equals(providedKey.trim())) {
            throw new UnauthorizedException("Invalid or missing " + INTEGRATION_KEY_HEADER + " header");
        }
    }
}
