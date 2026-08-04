package com.los.core.service.esign;

import com.los.core.model.entity.Document;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.document.IDocumentService;
import com.los.core.service.document.storage.DocumentBlobStore;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * On eSign simulate / mark-complete, registers KFS / program terms / additional multi-doc
 * PDFs under signed document types so they appear in the application Documents section.
 * <p>
 * Registration is idempotent per document type (skip when a latest doc of that SIGNED_* type already exists).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsignSignedApplicationDocumentService {

    public static final String SIGNED_KFS = "SIGNED_KFS";
    public static final String SIGNED_PROGRAM_TERMS = "SIGNED_PROGRAM_TERMS";
    public static final String SIGNED_SANCTION_TERMS = "SIGNED_SANCTION_TERMS";
    public static final String SIGNED_AGREEMENT = "SIGNED_AGREEMENT";
    public static final String SIGNED_PREFIX = "SIGNED_";

    private final KfsDocumentRepository kfsDocumentRepository;
    private final KfsPdfGenerationService kfsPdfGenerationService;
    private final IDocumentService documentService;
    private final DocumentRepository documentRepository;
    private final DocumentBlobStore documentBlobStore;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ActiveWorkflowConfigService activeWorkflowConfigService;

    /**
     * Generate / copy current signing PDFs into matching SIGNED_* document types (once each).
     */
    public void registerSignedDocumentsFromKfs(UUID applicationId) {
        if (applicationId == null) {
            return;
        }
        try {
            KfsDocument kfs = kfsDocumentRepository
                    .findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                    .orElse(null);
            if (kfs != null) {
                String signedType = resolveSignedDocumentType(kfs);
                if (!hasLatestDocument(applicationId, signedType)) {
                    byte[] unsignedPdf = kfsPdfGenerationService.generateKfsPdf(kfs);
                    documentService.storeDocumentBytes(
                            applicationId,
                            signedType,
                            fileNameFor(signedType),
                            "application/pdf",
                            unsignedPdf);
                    log.info("[eSign docs] Registered {} for application {}", signedType, applicationId);
                } else {
                    log.info("[eSign docs] Skip re-register {} — already present for application {}",
                            signedType, applicationId);
                }
            } else {
                log.warn("[eSign docs] No KFS row for application {} — skipping default SIGNED_* registration",
                        applicationId);
            }
            registerSignedAdditionalDocuments(applicationId);
        } catch (Exception e) {
            log.warn("[eSign docs] Failed to register signed docs for {}: {}",
                    applicationId, e.getMessage());
        }
    }

    /**
     * For workflow-configured multi-doc additionals: copy the latest upload into {@code SIGNED_<TYPE>}
     * if not already registered (admin late-upload + mark complete).
     */
    private void registerSignedAdditionalDocuments(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        if (app == null) {
            return;
        }
        EsignDocumentsConfig.Settings docs = activeWorkflowConfigService.findActiveForApplication(app)
                .map(EsignDocumentsConfig::fromWorkflow)
                .orElseGet(EsignDocumentsConfig.Settings::singleDefault);
        for (EsignDocumentsConfig.SystemDoc d : docs.enabledSystemExtras()) {
            promoteSourceToSigned(applicationId, d.documentKey().trim().toUpperCase(Locale.ROOT));
        }
        for (EsignDocumentsConfig.AdditionalDoc d : docs.additional()) {
            if (d.documentType() == null || d.documentType().isBlank()) {
                continue;
            }
            promoteSourceToSigned(applicationId, d.documentType().trim().toUpperCase(Locale.ROOT));
        }
    }

    private void promoteSourceToSigned(UUID applicationId, String sourceType) {
        String signedType = SIGNED_PREFIX + sourceType;
        if (hasLatestDocument(applicationId, signedType)) {
            log.debug("[eSign docs] Skip re-register {} — already present", signedType);
            return;
        }
        List<Document> uploads = documentRepository
                .findByApplicationIdAndDocumentTypeOrderByVersionNumberDesc(applicationId, sourceType);
        if (uploads == null || uploads.isEmpty()) {
            log.warn("[eSign docs] No document {} to promote as {} for application {}",
                    sourceType, signedType, applicationId);
            return;
        }
        Document latest = uploads.get(0);
        try (InputStream in = documentBlobStore.getObject(latest.getStorageKey())) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return;
            }
            String fileName = latest.getFileName() != null && !latest.getFileName().isBlank()
                    ? "signed-" + latest.getFileName()
                    : "signed-" + sourceType.toLowerCase(Locale.ROOT) + ".pdf";
            String contentType = latest.getContentType() != null ? latest.getContentType() : "application/pdf";
            documentService.storeDocumentBytes(applicationId, signedType, fileName, contentType, bytes);
            log.info("[eSign docs] Registered {} from {} for application {}",
                    signedType, sourceType, applicationId);
        } catch (Exception ex) {
            log.warn("[eSign docs] Failed to promote {} → {}: {}", sourceType, signedType, ex.getMessage());
        }
    }

    public void registerProviderSignedAgreement(UUID applicationId, byte[] providerSignedPdfBytes) {
        if (applicationId == null || providerSignedPdfBytes == null || providerSignedPdfBytes.length == 0) {
            return;
        }
        try {
            if (hasLatestDocument(applicationId, SIGNED_AGREEMENT)) {
                log.info("[eSign docs] Skip re-register {} — already present for application {}",
                        SIGNED_AGREEMENT, applicationId);
                return;
            }
            documentService.storeDocumentBytes(
                    applicationId,
                    SIGNED_AGREEMENT,
                    "signed-agreement.pdf",
                    "application/pdf",
                    providerSignedPdfBytes);
            log.info("[eSign docs] Registered {} for application {}", SIGNED_AGREEMENT, applicationId);
        } catch (Exception e) {
            log.warn("[eSign docs] Failed to register signed agreement PDF for {}: {}",
                    applicationId, e.getMessage());
        }
    }

    private boolean hasLatestDocument(UUID applicationId, String documentType) {
        List<Document> existing = documentRepository
                .findByApplicationIdAndDocumentTypeOrderByVersionNumberDesc(applicationId, documentType);
        return existing != null && !existing.isEmpty();
    }

    private static String resolveSignedDocumentType(KfsDocument kfs) {
        Map<String, Object> additional = kfs.getAdditionalTerms();
        Object kind = additional != null ? additional.get("documentKind") : null;
        String k = kind != null ? kind.toString().trim().toUpperCase(Locale.ROOT) : "";
        if (KfsService.DOCUMENT_KIND_ANCHOR_PROGRAM_TERMS.equals(k)) {
            return SIGNED_PROGRAM_TERMS;
        }
        if (KfsService.DOCUMENT_KIND_INVOICE_DISCOUNTING_TERMS.equals(k)
                || k.contains("SANCTION")) {
            return SIGNED_SANCTION_TERMS;
        }
        return SIGNED_KFS;
    }

    private static String fileNameFor(String signedType) {
        return switch (signedType) {
            case SIGNED_PROGRAM_TERMS -> "signed-program-terms.pdf";
            case SIGNED_SANCTION_TERMS -> "signed-sanction-terms.pdf";
            default -> "signed-kfs.pdf";
        };
    }
}
