package com.los.core.service.document;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.model.entity.Document;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.KycStepType;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.document.storage.DocumentBlobStore;
import com.los.core.service.esign.EsignDocumentsConfig;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.lowagie.text.pdf.PdfReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements IDocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentBlobStore documentBlobStore;
    private final AuditService auditService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ActiveWorkflowConfigService activeWorkflowConfigService;

    private static final Set<String> REQUIRED_DOC_TYPES = Set.of(
            "PAN_CARD", "AADHAAR", "BANK_STATEMENT", "PHOTOGRAPH"
    );

    @Override
    public DocumentResponse uploadDocument(UUID applicationId, String documentType, MultipartFile file, KycStepType kycStepType) {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            originalFileName = "document";
        }
        String storageKey = String.format("%s/%s/%s_%s",
                applicationId, documentType, UUID.randomUUID(), originalFileName);

        try {
            byte[] fileBytes = file.getBytes();
            validateEsignSigningUpload(applicationId, documentType, originalFileName, file.getContentType(), fileBytes);
            String checksum = computeSha256(fileBytes);
            String contentType = normalizeContentType(file.getContentType(), originalFileName);
            documentBlobStore.putObject(storageKey, fileBytes, fileBytes.length, contentType);

            Document document = Document.builder()
                    .applicationId(applicationId)
                    .documentType(documentType)
                    .kycStepType(kycStepType)
                    .fileName(originalFileName)
                    .storageKey(storageKey)
                    .contentType(contentType)
                    .fileSize(file.getSize())
                    .checksum(checksum)
                    .build();

            document = documentRepository.save(document);
            log.info("Document uploaded: {} for application {}", documentType, applicationId);

            auditService.logEvent(applicationId, "DOCUMENT", "UPLOADED",
                    null, null,
                    Map.of("documentType", documentType, "fileName", originalFileName),
                    "Document uploaded: " + documentType);

            return toResponse(document);
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload document: {}", e.getMessage());
            throw new RuntimeException("Document upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Workflow-configured additional eSign documents must be valid PDFs with the expected page count
     * (admin review docs panel, borrower/portal create, PLP anchor upload all share this path).
     */
    private void validateEsignSigningUpload(
            UUID applicationId,
            String documentType,
            String fileName,
            String contentType,
            byte[] fileBytes) {
        if (documentType == null || documentType.isBlank() || fileBytes == null || fileBytes.length == 0) {
            return;
        }
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        if (app == null) {
            return;
        }
        EsignDocumentsConfig.Settings docs = activeWorkflowConfigService.findActiveForApplication(app)
                .map(EsignDocumentsConfig::fromWorkflow)
                .orElseGet(EsignDocumentsConfig.Settings::singleDefault);
        if (!docs.isAdditionalSigningType(documentType)) {
            return;
        }
        String type = documentType.trim().toUpperCase(Locale.ROOT);
        int expected = docs.expectedPageCountFor(type);
        String label = docs.labelFor(type);
        String name = fileName != null ? fileName : "";
        String ct = contentType != null ? contentType : "";
        boolean nameLooksPdf = name.toLowerCase(Locale.ROOT).endsWith(".pdf");
        boolean typeLooksPdf = ct.toLowerCase(Locale.ROOT).contains("pdf");
        if (!nameLooksPdf && !typeLooksPdf && !isPdfMagic(fileBytes)) {
            throw new BusinessRuleException(
                    label + " must be a PDF file (expected " + expected + " page"
                            + (expected == 1 ? "" : "s") + ").",
                    "ESIGN_DOCUMENT_INVALID",
                    "DOCUMENT_UPLOAD",
                    Map.of(
                            "documentType", type,
                            "expectedPageCount", expected,
                            "reason", "NOT_PDF"));
        }
        int actualPages;
        try {
            actualPages = countPdfPages(fileBytes);
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException(
                    "Invalid or unreadable PDF for " + label + ".",
                    "ESIGN_DOCUMENT_INVALID",
                    "DOCUMENT_UPLOAD",
                    Map.of(
                            "documentType", type,
                            "expectedPageCount", expected,
                            "reason", "UNREADABLE_PDF"));
        }
        if (actualPages != expected) {
            throw new BusinessRuleException(
                    label + " must have exactly " + expected + " page"
                            + (expected == 1 ? "" : "s")
                            + " (uploaded file has " + actualPages + ").",
                    "ESIGN_DOCUMENT_PAGE_COUNT",
                    "DOCUMENT_UPLOAD",
                    Map.of(
                            "documentType", type,
                            "expectedPageCount", expected,
                            "actualPageCount", actualPages));
        }
    }

    private static boolean isPdfMagic(byte[] bytes) {
        return bytes != null
                && bytes.length >= 4
                && bytes[0] == 0x25
                && bytes[1] == 0x50
                && bytes[2] == 0x44
                && bytes[3] == 0x46;
    }

    private static int countPdfPages(byte[] pdfBytes) {
        if (!isPdfMagic(pdfBytes)) {
            throw new BusinessRuleException(
                    "File is not a valid PDF.",
                    "ESIGN_DOCUMENT_INVALID",
                    "DOCUMENT_UPLOAD",
                    Map.of("reason", "NOT_PDF"));
        }
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfBytes);
            return reader.getNumberOfPages();
        } catch (Exception e) {
            throw new BusinessRuleException(
                    "Invalid or unreadable PDF.",
                    "ESIGN_DOCUMENT_INVALID",
                    "DOCUMENT_UPLOAD",
                    Map.of("reason", "UNREADABLE_PDF"));
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    @Override
    public DocumentResponse storeDocumentBytes(
            UUID applicationId,
            String documentType,
            String fileName,
            String contentType,
            byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Document bytes are required");
        }
        String originalFileName = (fileName == null || fileName.isBlank()) ? "document" : fileName.trim();
        String storageKey = String.format("%s/%s/%s_%s",
                applicationId, documentType, UUID.randomUUID(), originalFileName);
        try {
            String checksum = computeSha256(bytes);
            String ct = normalizeContentType(contentType, originalFileName);
            documentBlobStore.putObject(storageKey, bytes, bytes.length, ct);

            Document document = Document.builder()
                    .applicationId(applicationId)
                    .documentType(documentType)
                    .fileName(originalFileName)
                    .storageKey(storageKey)
                    .contentType(ct)
                    .fileSize((long) bytes.length)
                    .checksum(checksum)
                    .build();

            document = documentRepository.save(document);
            log.info("Document stored: {} for application {}", documentType, applicationId);

            auditService.logEvent(applicationId, "DOCUMENT", "UPLOADED",
                    null, null,
                    Map.of("documentType", documentType, "fileName", originalFileName, "source", "SERVICE"),
                    "Document stored: " + documentType);

            return toResponse(document);
        } catch (Exception e) {
            log.error("Failed to store document bytes: {}", e.getMessage());
            throw new RuntimeException("Document store failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DocumentResponse> getDocuments(UUID applicationId) {
        return documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public byte[] downloadDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        try (InputStream stream = documentBlobStore.getObject(document.getStorageKey())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to download document: {}", e.getMessage());
            throw new RuntimeException("Document download failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        try {
            documentBlobStore.removeObject(document.getStorageKey());
        } catch (Exception e) {
            log.warn("Failed to delete from object storage: {}", e.getMessage());
        }

        documentRepository.delete(document);
        log.info("Document deleted: {} from application {}", document.getDocumentType(), document.getApplicationId());
    }

    @Override
    public boolean isDocumentChecklistComplete(UUID applicationId) {
        List<Document> docs = documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        Set<String> uploadedTypes = docs.stream().map(Document::getDocumentType).collect(Collectors.toSet());
        return uploadedTypes.containsAll(REQUIRED_DOC_TYPES);
    }

    private static String normalizeContentType(String uploaded, String originalFileName) {
        String ct = uploaded;
        if (ct == null || ct.isBlank() || MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(ct.trim())) {
            String guessed = URLConnection.guessContentTypeFromName(originalFileName);
            if (guessed != null && !guessed.isBlank()) {
                ct = guessed;
            }
        }
        if (ct == null || ct.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return ct;
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private DocumentResponse toResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .applicationId(doc.getApplicationId())
                .documentType(doc.getDocumentType())
                .kycStepType(doc.getKycStepType())
                .fileName(doc.getFileName())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .checksum(doc.getChecksum())
                .uploadedBy(doc.getUploadedBy())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
