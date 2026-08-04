package com.los.core.service.esign;

import com.los.core.model.entity.Document;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.SanctionRecordRepository;
import com.los.core.service.document.IDocumentService;
import com.los.core.service.sanction.SanctionLetterPdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Ensures workflow-configured system eSign PDFs exist as application {@link Document} rows so
 * multi-document eSign can load them like uploads. Primary KFS / program-terms are not
 * materialized here (they remain on the KFS generation path).
 * <p>
 * Document templates stored on workflow config are intentionally unused — generation uses the
 * default built-in PDF procedures until template rendering is enabled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsignSystemDocumentMaterializer {

    private final DocumentRepository documentRepository;
    private final SanctionRecordRepository sanctionRecordRepository;
    private final SanctionLetterPdfService sanctionLetterPdfService;
    private final IDocumentService documentService;

    /**
     * Materialize each enabled non-primary system document (e.g. sanction letter) when missing.
     * Failures for a single document are logged; missing docs surface as multi-doc gaps later.
     */
    public void materializeEnabledExtras(UUID applicationId, LoanApplication app, EsignDocumentsConfig.Settings docs) {
        if (applicationId == null || app == null || docs == null) {
            return;
        }
        for (EsignDocumentsConfig.SystemDoc systemDoc : docs.enabledSystemExtras()) {
            if (systemDoc.templateBase64() != null && !systemDoc.templateBase64().isBlank()) {
                log.debug(
                        "Workflow eSign system template present for {} but unused — default generation procedure",
                        systemDoc.documentKey());
            }
            String key = systemDoc.documentKey().trim().toUpperCase(Locale.ROOT);
            if (EsignDocumentsConfig.DOCUMENT_KEY_SANCTION_LETTER.equals(key)) {
                ensureSanctionLetter(applicationId, app);
            } else {
                log.info(
                        "Skipping unknown eSign system document key {} for application {} (not generated)",
                        key, applicationId);
            }
        }
    }

    private void ensureSanctionLetter(UUID applicationId, LoanApplication app) {
        if (hasDocument(applicationId, EsignDocumentsConfig.DOCUMENT_KEY_SANCTION_LETTER)) {
            log.debug("Sanction letter already stored for application {}", applicationId);
            return;
        }
        SanctionRecord rec = sanctionRecordRepository
                .findTopByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElse(null);
        if (rec == null) {
            log.warn(
                    "Cannot materialize sanction letter for eSign — no sanction record for application {}",
                    applicationId);
            return;
        }
        try {
            // Built-in 2-page standard letter (templateBase64 not used).
            byte[] pdf = sanctionLetterPdfService.renderStandaloneTwoPageSanctionLetter(app, rec);
            if (pdf == null || pdf.length == 0) {
                log.warn("Empty sanction letter PDF for application {}", applicationId);
                return;
            }
            documentService.storeDocumentBytes(
                    applicationId,
                    EsignDocumentsConfig.DOCUMENT_KEY_SANCTION_LETTER,
                    "sanction-letter.pdf",
                    "application/pdf",
                    pdf);
            log.info("Materialized system SANCTION_LETTER for eSign on application {}", applicationId);
        } catch (Exception e) {
            log.warn(
                    "Failed to materialize SANCTION_LETTER for application {}: {}",
                    applicationId, e.getMessage());
        }
    }

    private boolean hasDocument(UUID applicationId, String documentType) {
        List<Document> found = documentRepository
                .findByApplicationIdAndDocumentTypeOrderByVersionNumberDesc(applicationId, documentType);
        return found != null && !found.isEmpty();
    }
}
