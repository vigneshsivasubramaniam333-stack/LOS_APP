package com.los.core.service.flow.step;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.SanctionRecordRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.config.EsignNotificationProperties;
import com.los.core.service.esign.EsignDocumentsConfig;
import com.los.core.service.esign.EsignSigningLinkNotifier;
import com.los.core.service.esign.EsignRequestTrackingService;
import com.los.core.service.esign.EsignSystemDocumentMaterializer;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.ApplicationPartyService;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.los.core.model.entity.ApplicationParty;
import com.los.core.model.entity.Document;
import com.los.core.model.enums.ApplicationPartyRole;
import com.los.core.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@link FlowStepType#ESIGN} — eSign initiation via {@link IIntegrationRouterService}. Moved from
 * {@code LoanApplicationFlowService#initiateESign}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsignInitiateStepExecutor implements IStepExecutor {

    private final LoanApplicationRepository applicationRepository;
    private final KfsDocumentRepository kfsDocumentRepository;
    private final SanctionRecordRepository sanctionRecordRepository;
    private final KfsService kfsService;
    private final IIntegrationRouterService integrationRouter;
    private final AuditService auditService;
    private final EsignRequestTrackingService esignRequestTrackingService;
    private final EsignSigningLinkNotifier esignSigningLinkNotifier;
    private final EsignNotificationProperties esignNotificationProperties;
    private final ApplicationPartyService applicationPartyService;
    private final ActiveWorkflowConfigService activeWorkflowConfigService;
    private final DocumentRepository documentRepository;
    private final EsignSystemDocumentMaterializer esignSystemDocumentMaterializer;

    @Override
    public boolean supports(String stepType) {
        return FlowStepType.ESIGN.equals(stepType);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public StepResult execute(UUID applicationId, Map<String, Object> context) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.los.core.exception.ResourceNotFoundException("Application not found: " + applicationId));
        app = ensureInvoiceDiscountingBorrowerTermsReady(applicationId, app);
        if (!esignAllowedForStatus(app)) {
            auditService.logEvent(applicationId, "PREREQUISITE_BLOCK", "ESIGN_BLOCKED",
                    null,
                    Map.of("status", app.getStatus().name(), "reason", "KFS_NOT_READY", "action", "ESIGN_INITIATE"),
                    null,
                    "eSign blocked: requires KFS_GENERATED, SANCTION_ISSUED, anchor ESIGN_PENDING, or invoice discounting borrower SANCTIONED");
            throw new BusinessRuleException(
                    "Cannot initiate eSign — application must be in KFS_GENERATED, SANCTION_ISSUED, "
                            + "anchor ESIGN_PENDING (program terms), or invoice discounting borrower SANCTIONED. Current: "
                            + app.getStatus(),
                    "ESIGN_STATUS_INVALID",
                    "ESIGN_INITIATE",
                    Map.of("status", app.getStatus().name())
            );
        }
        Map<String, Object> signerInfo = (context != null && context.get("signerInfo") != null)
                ? (Map<String, Object>) context.get("signerInfo")
                : Map.of();
        applicationPartyService.ensurePrimaryParty(app);
        boolean multiParty = applicationPartyService.isMultiPartyEnabled(app)
                && applicationPartyService.partiesRequiredForDisbursement(applicationId).size() > 1;

        if (multiParty) {
            return initiateForAllRequiredParties(applicationId, app, signerInfo);
        }

        Map<String, Object> signerForRoute = ApplicationPartyResolver.enrichEsignSignerInfo(app, signerInfo);
        EsignDocumentsConfig.Settings docs = resolveEsignDocuments(app);
        esignSystemDocumentMaterializer.materializeEnabledExtras(applicationId, app, docs);
        if (!docs.hasMultiDocumentSigning()) {
            return initiateForSingleSigner(applicationId, app, signerForRoute, null, docs.defaultDocumentKey(), docs);
        }
        return initiateMultiDocument(applicationId, app, signerForRoute, docs);
    }

    private StepResult initiateMultiDocument(
            UUID applicationId,
            LoanApplication app,
            Map<String, Object> signerForRoute,
            EsignDocumentsConfig.Settings docs) {
        DocumentAvailability availability = resolveAvailableDocumentKeys(applicationId, docs);
        List<Map<String, Object>> docResults = new ArrayList<>();
        boolean anyFail = !availability.missingRequired().isEmpty();
        String primaryTxn = null;
        String primaryUrl = "";

        for (String documentKey : availability.keysToInitiate()) {
            StepResult one = initiateForSingleSigner(applicationId, app, signerForRoute, null, documentKey, docs);
            Map<String, Object> out = one.output() != null ? new HashMap<>(one.output()) : new HashMap<>();
            out.put("documentKey", documentKey);
            docResults.add(out);
            boolean ok = Boolean.TRUE.equals(out.get("esignSuccess"))
                    || "true".equalsIgnoreCase(String.valueOf(out.get("esignSuccess")));
            if (!ok) {
                anyFail = true;
            } else if (primaryTxn == null || primaryTxn.isBlank()) {
                primaryTxn = String.valueOf(out.getOrDefault("transactionId", ""));
                primaryUrl = String.valueOf(out.getOrDefault("signingUrl", ""));
            }
        }
        for (String missing : availability.missingRequired()) {
            Map<String, Object> missingOut = new HashMap<>();
            missingOut.put("documentKey", missing);
            missingOut.put("documentLabel", docs.labelFor(missing));
            missingOut.put("esignSuccess", false);
            missingOut.put("errorMessage", "Document not uploaded — upload from application Documents and re-initiate eSign");
            missingOut.put("missing", true);
            docResults.add(missingOut);
        }
        app = applicationRepository.findById(applicationId).orElse(app);
        Map<String, Object> aggregate = new HashMap<>();
        aggregate.put("applicationId", applicationId);
        aggregate.put("applicationNumber", app.getApplicationNumber());
        aggregate.put("status", app.getStatus().name());
        aggregate.put("esignSuccess", !anyFail);
        aggregate.put("transactionId", primaryTxn != null ? primaryTxn : "");
        aggregate.put("signingUrl", primaryUrl);
        aggregate.put("errorMessage", buildMultiDocErrorMessage(availability.missingRequired(), anyFail));
        aggregate.put("missingDocumentTypes", availability.missingRequired());
        aggregate.put("documentResults", docResults);
        aggregate.put("multiDocument", true);
        return anyFail ? StepResult.fail(aggregate) : StepResult.ok(aggregate);
    }

    private EsignDocumentsConfig.Settings resolveEsignDocuments(LoanApplication app) {
        try {
            return activeWorkflowConfigService.findActiveForApplication(app)
                    .map(EsignDocumentsConfig::fromWorkflow)
                    .orElseGet(EsignDocumentsConfig.Settings::singleDefault);
        } catch (Exception e) {
            log.warn("Could not resolve esign signing documents from workflow for {}: {}", app.getId(), e.getMessage());
            return EsignDocumentsConfig.Settings.singleDefault();
        }
    }

    /**
     * Resolves which configured signing documents can be initiated now.
     * Missing required additionals / system extras are reported (not hard-blocked) so default
     * KFS/terms and available docs still generate signing URLs/emails.
     */
    private DocumentAvailability resolveAvailableDocumentKeys(UUID applicationId, EsignDocumentsConfig.Settings docs) {
        List<String> keys = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();
        keys.add(docs.defaultDocumentKey());
        for (EsignDocumentsConfig.SystemDoc d : docs.enabledSystemExtras()) {
            String type = d.documentKey().trim().toUpperCase(Locale.ROOT);
            if (hasUploadedDocument(applicationId, type)) {
                keys.add(type);
            } else {
                // System extras are required when enabled — generation should have materialised them.
                missingRequired.add(type);
            }
        }
        for (EsignDocumentsConfig.AdditionalDoc d : docs.additional()) {
            if (d.documentType() == null || d.documentType().isBlank()) {
                continue;
            }
            String type = d.documentType().trim();
            boolean present = hasUploadedDocument(applicationId, type);
            if (present) {
                keys.add(type);
            } else if (d.required()) {
                missingRequired.add(type);
            }
        }
        return new DocumentAvailability(keys, missingRequired);
    }

    private boolean hasUploadedDocument(UUID applicationId, String documentType) {
        List<Document> found = documentRepository
                .findByApplicationIdAndDocumentTypeOrderByVersionNumberDesc(applicationId, documentType);
        return found != null && !found.isEmpty();
    }

    private static String buildMultiDocErrorMessage(List<String> missingRequired, boolean anyFail) {
        if (missingRequired != null && !missingRequired.isEmpty()) {
            return "Missing documents for signing (generated for available documents only): "
                    + String.join(", ", missingRequired);
        }
        return anyFail ? "One or more document eSign initiations failed" : "";
    }

    private record DocumentAvailability(List<String> keysToInitiate, List<String> missingRequired) {}

    private StepResult initiateForAllRequiredParties(
            UUID applicationId, LoanApplication app, Map<String, Object> baseSignerInfo) {
        EsignDocumentsConfig.Settings docs = resolveEsignDocuments(app);
        esignSystemDocumentMaterializer.materializeEnabledExtras(applicationId, app, docs);
        DocumentAvailability availability = resolveAvailableDocumentKeys(applicationId, docs);
        List<String> keys = availability.keysToInitiate();

        List<ApplicationParty> parties = applicationPartyService.partiesRequiredForDisbursement(applicationId);
        List<Map<String, Object>> partyResults = new ArrayList<>();
        boolean anyFail = !availability.missingRequired().isEmpty();
        String primaryTxn = null;
        String primaryUrl = "";

        for (ApplicationParty party : parties) {
            Map<String, Object> signerForRoute = ApplicationPartyResolver.enrichEsignSignerInfoFromParty(
                    party, baseSignerInfo);
            List<Map<String, Object>> docResults = new ArrayList<>();
            boolean partyFail = !availability.missingRequired().isEmpty();
            for (String documentKey : keys) {
                StepResult one = initiateForSingleSigner(
                        applicationId, app, signerForRoute, party, documentKey, docs);
                Map<String, Object> out = one.output() != null ? new HashMap<>(one.output()) : new HashMap<>();
                out.put("documentKey", documentKey);
                docResults.add(out);
                boolean ok = Boolean.TRUE.equals(out.get("esignSuccess"))
                        || "true".equalsIgnoreCase(String.valueOf(out.get("esignSuccess")));
                if (!ok) {
                    partyFail = true;
                } else if (party.getRole() == ApplicationPartyRole.PRIMARY
                        && (primaryTxn == null || primaryTxn.isBlank())) {
                    primaryTxn = String.valueOf(out.getOrDefault("transactionId", ""));
                    primaryUrl = String.valueOf(out.getOrDefault("signingUrl", ""));
                }
            }
            for (String missing : availability.missingRequired()) {
                Map<String, Object> missingOut = new HashMap<>();
                missingOut.put("documentKey", missing);
                missingOut.put("documentLabel", docs.labelFor(missing));
                missingOut.put("esignSuccess", false);
                missingOut.put("errorMessage", "Document not uploaded — upload from application Documents and re-initiate eSign");
                missingOut.put("missing", true);
                docResults.add(missingOut);
            }
            Map<String, Object> partyOut = new HashMap<>();
            partyOut.put("partyId", party.getId().toString());
            partyOut.put("partyRole", party.getRole().name());
            partyOut.put("documentResults", docResults);
            partyOut.put("esignSuccess", !partyFail);
            partyResults.add(partyOut);
            if (partyFail) {
                anyFail = true;
            }
        }

        app = applicationRepository.findById(applicationId).orElse(app);
        Map<String, Object> aggregate = new HashMap<>();
        aggregate.put("applicationId", applicationId);
        aggregate.put("applicationNumber", app.getApplicationNumber());
        aggregate.put("status", app.getStatus().name());
        aggregate.put("esignSuccess", !anyFail);
        aggregate.put("transactionId", primaryTxn != null ? primaryTxn : "");
        aggregate.put("signingUrl", primaryUrl);
        aggregate.put("errorMessage", buildMultiDocErrorMessage(availability.missingRequired(), anyFail));
        aggregate.put("missingDocumentTypes", availability.missingRequired());
        aggregate.put("partyResults", partyResults);
        aggregate.put("multiParty", true);
        aggregate.put("multiDocument", keys.size() > 1 || !availability.missingRequired().isEmpty());
        return anyFail ? StepResult.fail(aggregate) : StepResult.ok(aggregate);
    }

    private StepResult initiateForSingleSigner(
            UUID applicationId,
            LoanApplication app,
            Map<String, Object> signerForRoute,
            ApplicationParty party,
            String documentKey,
            EsignDocumentsConfig.Settings docs) {
        Object email = signerForRoute.get("email");
        Object borrowerEmail = signerForRoute.get("borrowerEmail");
        if ((borrowerEmail == null || borrowerEmail.toString().isBlank())
                && email != null
                && !email.toString().isBlank()) {
            signerForRoute.put("borrowerEmail", email.toString().trim());
        }

        String docKey = documentKey != null && !documentKey.isBlank() ? documentKey : "KFS_AGREEMENT";
        int expectedPages = docs != null
                ? docs.expectedPageCountFor(docKey)
                : EsignDocumentsConfig.DEFAULT_EXPECTED_PAGE_COUNT;
        String documentLabel = docs != null ? docs.labelFor(docKey) : docKey;
        Map<String, Object> signerWithMeta = new HashMap<>(signerForRoute);
        signerWithMeta.put("expectedPageCount", expectedPages);
        signerWithMeta.put("documentLabel", documentLabel);
        Map<String, Object> esignPayload = new HashMap<>();
        esignPayload.put("documentKey", docKey);
        esignPayload.put("expectedPageCount", expectedPages);
        esignPayload.put("documentLabel", documentLabel);
        esignPayload.put("signerInfo", signerWithMeta);
        esignPayload.put("esignStepType", "ESIGN_AGREEMENT");
        if (party != null && party.getId() != null) {
            esignPayload.put("partyId", party.getId().toString());
        }
        IIntegrationRouterService.ESignRouteResult result = integrationRouter.routeESignRequest(applicationId, esignPayload);

        Map<String, Object> meta = result.providerMetadata();
        boolean reusedSigningUrl = meta != null && Boolean.TRUE.equals(meta.get("reusedSigningUrl"));

        if (result.success()) {
            app.setStatus(ApplicationStatus.ESIGN_PENDING);
            if (party == null || party.getRole() == ApplicationPartyRole.PRIMARY) {
                if ("KFS_AGREEMENT".equalsIgnoreCase(docKey)
                        || "ANCHOR_PROGRAM_TERMS".equalsIgnoreCase(docKey)
                        || EsignDocumentsConfig.DOCUMENT_KEY_SANCTION_LETTER.equalsIgnoreCase(docKey)
                        || app.getEsignTransactionId() == null) {
                    app.setEsignTransactionId(result.transactionId());
                }
            }
            app.setCurrentStepStartedAt(Instant.now());
            app = applicationRepository.save(app);

            auditService.logEvent(applicationId, "FLOW", "ESIGN_INITIATED",
                    null, Map.of("status", app.getStatus().name()),
                    Map.of("status", "ESIGN_PENDING",
                            "esignTransactionId", result.transactionId(),
                            "documentKey", docKey,
                            "documentLabel", documentLabel,
                            "partyId", party != null && party.getId() != null ? party.getId().toString() : ""),
                    "eSign initiated");

            log.info("eSign initiated for {} — txnId: {}, documentKey: {}, signingUrl: {}, reusedSigningUrl: {}, partyId: {}",
                    app.getApplicationNumber(), result.transactionId(), docKey, result.signingUrl(), reusedSigningUrl,
                    party != null ? party.getId() : null);
            if (!reusedSigningUrl) {
                try {
                    esignRequestTrackingService.recordInitiationSuccess(
                            applicationId,
                            docKey,
                            result.providerName(),
                            result.transactionId(),
                            result.signingUrl(),
                            signerWithMeta,
                            meta,
                            "ESIGN_AGREEMENT",
                            party != null ? party.getId() : null);
                } catch (Exception ex) {
                    log.warn("Failed to persist esign_requests for application {}: {}", applicationId, ex.getMessage());
                }
            } else {
                log.info("[ESIGN_PERSIST] skipped persistence (reused existing esign_requests URL) appId={} workflowId={}",
                        applicationId, result.transactionId());
            }

            if (party != null) {
                applicationPartyService.markPartyEsignPending(party);
            }
            // Always notify for a newly created signing URL; additional documents must each get their own email.
            dispatchSigningLinkEmail(
                    applicationId, app, signerWithMeta, result, reusedSigningUrl, docKey, documentLabel);
        } else {
            log.error("eSign initiation failed for {} documentKey={}: {}",
                    app.getApplicationNumber(), docKey, result.errorMessage());
        }

        Map<String, Object> out = Map.of(
                "applicationId", applicationId,
                "applicationNumber", app.getApplicationNumber(),
                "status", app.getStatus().name(),
                "esignSuccess", result.success(),
                "transactionId", result.transactionId() != null ? result.transactionId() : "",
                "signingUrl", result.signingUrl() != null ? result.signingUrl() : "",
                "documentKey", docKey,
                "documentLabel", documentLabel,
                "errorMessage", result.errorMessage() != null ? result.errorMessage() : ""
        );
        return StepResult.ok(out);
    }

    private LoanApplication ensureInvoiceDiscountingBorrowerTermsReady(UUID applicationId, LoanApplication app) {
        if (!InvoiceDiscountingApplicationRules.isBorrowerFlow(app)
                || app.getStatus() != ApplicationStatus.SANCTIONED) {
            return app;
        }
        if (kfsDocumentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).isPresent()) {
            app.setStatus(ApplicationStatus.KFS_GENERATED);
            app.setCurrentStepStartedAt(Instant.now());
            return applicationRepository.save(app);
        }
        SanctionRecord rec = sanctionRecordRepository.findTopByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Cannot initiate eSign — sanction record missing for invoice discounting borrower",
                        "SANCTION_RECORD_MISSING",
                        "ESIGN_INITIATE",
                        Map.of("status", ApplicationStatus.SANCTIONED.name())));
        kfsService.generateInvoiceDiscountingBorrowerTermsDocument(applicationId, rec, Map.of());
        app.setStatus(ApplicationStatus.KFS_GENERATED);
        app.setCurrentStepStartedAt(Instant.now());
        LoanApplication saved = applicationRepository.save(app);
        auditService.logEvent(applicationId, "FLOW", "SANCTION_TERMS_BACKFILL",
                null, Map.of("status", "SANCTIONED"),
                Map.of("status", "KFS_GENERATED", "documentKind", "INVOICE_DISCOUNTING_TERMS"),
                "Legacy invoice discounting borrower — terms document backfilled for eSign");
        log.info("Backfilled invoice discounting terms document for {} — status KFS_GENERATED",
                saved.getApplicationNumber());
        return saved;
    }

    private static boolean esignAllowedForStatus(LoanApplication app) {
        if (app.getStatus() == ApplicationStatus.ESIGN_PENDING
                && InvoiceDiscountingApplicationRules.isAnchorFlow(app)) {
            return true;
        }
        if (app.getStatus() == ApplicationStatus.SANCTION_ISSUED
                || app.getStatus() == ApplicationStatus.KFS_GENERATED) {
            return true;
        }
        return app.getStatus() == ApplicationStatus.SANCTIONED
                && InvoiceDiscountingApplicationRules.isBorrowerFlow(app);
    }

    private void dispatchSigningLinkEmail(
            UUID applicationId,
            LoanApplication app,
            Map<String, Object> signerForRoute,
            IIntegrationRouterService.ESignRouteResult result,
            boolean reusedSigningUrl,
            String documentKey,
            String documentLabel) {
        if (!esignNotificationProperties.isEnabled()) {
            log.info("[ESIGN_EMAIL] skipped (los.esign.notification.enabled=false) applicationId={} documentKey={}",
                    applicationId, documentKey);
            return;
        }
        // Always email when a signing URL exists — including multi-doc additionals and reuse of stored URLs.
        // Reused KFS links used to skip here, which also blocked additional-document emails in multi-doc loops.
        String rawEmail = borrowerEmailRaw(signerForRoute);
        if (rawEmail == null || rawEmail.isBlank()) {
            log.warn("[ESIGN_EMAIL] skipped (no borrower email on signer info) applicationId={} documentKey={}",
                    applicationId, documentKey);
            return;
        }
        String signingUrl = result.signingUrl() != null ? result.signingUrl().trim() : "";
        if (signingUrl.isBlank()) {
            log.warn("[ESIGN_EMAIL] skipped (empty signingUrl) applicationId={} documentKey={}",
                    applicationId, documentKey);
            return;
        }
        String name = signerForRoute.get("name") != null ? signerForRoute.get("name").toString().trim() : "Borrower";
        try {
            esignSigningLinkNotifier.publishSigningLinkEmail(
                    applicationId,
                    app.getApplicationNumber(),
                    name,
                    List.of(rawEmail.trim()),
                    signingUrl,
                    esignNotificationProperties.getTemplateCode(),
                    esignNotificationProperties.getLinkExpiryHours(),
                    reusedSigningUrl,
                    documentKey,
                    documentLabel);
        } catch (Exception ex) {
            log.error("[ESIGN_EMAIL] unexpected failure applicationId={} documentKey={}: {}",
                    applicationId, documentKey, ex.getMessage(), ex);
        }
    }

    private static String borrowerEmailRaw(Map<String, Object> signerInfo) {
        if (signerInfo == null) {
            return null;
        }
        Object v = firstNonNullObj(
                signerInfo.get("borrowerEmail"),
                signerInfo.get("email"),
                signerInfo.get("signerEmail"),
                signerInfo.get("EmailId"));
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Object firstNonNullObj(Object... o) {
        for (Object x : o) {
            if (x != null) {
                return x;
            }
        }
        return null;
    }
}
