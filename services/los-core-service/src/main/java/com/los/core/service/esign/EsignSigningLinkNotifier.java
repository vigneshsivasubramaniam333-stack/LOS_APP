package com.los.core.service.esign;

import com.los.core.config.RabbitMQConfig;
import com.los.core.service.workflow.WorkflowNotificationResolverService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes eSign signing-link emails to {@code los.notification} (queue {@code notification.email}).
 * Payload fields match notification-service {@code NotificationEvent} for JSON deserialization.
 * <p>
 * Multi-document eSign: each document type publishes a separate email (own signing URL + labels).
 * Sends run after the current DB transaction commits so Rabbit delivery is not lost on rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsignSigningLinkNotifier {

    /** Same template code rendered in notification-service ({@link com.los.notification.template.NotificationTemplateEngine}). */
    public static final String TEMPLATE_ESIGN_PENDING = "ESIGN_PENDING";

    private final RabbitTemplate rabbitTemplate;
    private final WorkflowNotificationResolverService workflowNotificationResolverService;

    /**
     * @param recipientEmails validated non-blank borrower email(s)
     */
    public void publishSigningLinkEmail(
            UUID applicationId,
            String applicationNumber,
            String borrowerName,
            List<String> recipientEmails,
            String signingUrl,
            String templateCode,
            int expiryHours,
            boolean reusedSigningUrl) {
        publishSigningLinkEmail(
                applicationId,
                applicationNumber,
                borrowerName,
                recipientEmails,
                signingUrl,
                templateCode,
                expiryHours,
                reusedSigningUrl,
                null,
                null);
    }

    public void publishSigningLinkEmail(
            UUID applicationId,
            String applicationNumber,
            String borrowerName,
            List<String> recipientEmails,
            String signingUrl,
            String templateCode,
            int expiryHours,
            boolean reusedSigningUrl,
            String documentType,
            String documentLabel) {
        if (recipientEmails == null || recipientEmails.stream().noneMatch(e -> e != null && !e.isBlank())) {
            log.warn("[ESIGN_EMAIL] skipped (no recipients) applicationId={} applicationNumber={} reusedSigningUrl={} documentType={}",
                    applicationId, maskAppNumber(applicationNumber), reusedSigningUrl, documentType);
            return;
        }
        List<String> to = recipientEmails.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        if (to.isEmpty()) {
            log.warn("[ESIGN_EMAIL] skipped (empty after sanitize) applicationId={} documentType={}", applicationId, documentType);
            return;
        }
        if (signingUrl == null || signingUrl.isBlank()) {
            log.warn("[ESIGN_EMAIL] skipped (empty signingUrl) applicationId={} documentType={}", applicationId, documentType);
            return;
        }

        Map<String, Object> templateData = new LinkedHashMap<>();
        templateData.put("borrowerName", borrowerName != null ? borrowerName : "Borrower");
        templateData.put("applicationNumber", applicationNumber != null ? applicationNumber : "");
        templateData.put("esignLink", signingUrl);
        templateData.put("expiryHours", expiryHours);
        templateData.put("eventType", "ESIGN_LINK");
        // Unique correlation for multi-doc so notification / SMTP logs can distinguish messages.
        templateData.put("notificationUid", UUID.randomUUID().toString());
        String resolvedLabel = documentLabel != null && !documentLabel.isBlank()
                ? documentLabel
                : (documentType != null && !documentType.isBlank() ? documentType.replace('_', ' ') : "Agreement");
        templateData.put("documentLabel", resolvedLabel);
        if (documentType != null && !documentType.isBlank()) {
            templateData.put("documentType", documentType.trim());
        }

        String code = templateCode != null && !templateCode.isBlank() ? templateCode : TEMPLATE_ESIGN_PENDING;

        Runnable send = () -> doPublish(applicationId, applicationNumber, to, code, templateData, documentType, reusedSigningUrl);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        send.run();
                    } catch (Exception e) {
                        log.error("[ESIGN_EMAIL] afterCommit publish failed applicationId={} documentType={}: {}",
                                applicationId, documentType, e.getMessage(), e);
                    }
                }
            });
            log.info("[ESIGN_EMAIL] scheduled after-commit applicationId={} documentType={} documentLabel={} recipientCount={}",
                    applicationId, documentType, resolvedLabel, to.size());
        } else {
            send.run();
        }
    }

    private void doPublish(
            UUID applicationId,
            String applicationNumber,
            List<String> to,
            String code,
            Map<String, Object> templateData,
            String documentType,
            boolean reusedSigningUrl) {
        String exchange = RabbitMQConfig.EXCHANGE;
        // Prefer workflow process mappings, but always fall back so multi-doc never drops emails.
        var actions = workflowNotificationResolverService.resolveForApplication(
                applicationId,
                "ESIGN_KFS",
                "ESIGN_LINK",
                to,
                code,
                "EMAIL");
        if (actions == null || actions.isEmpty()) {
            actions = workflowNotificationResolverService.resolveForApplication(
                    applicationId,
                    "ESIGN_AGREEMENT",
                    "ESIGN_LINK",
                    to,
                    code,
                    "EMAIL");
        }
        if (actions == null || actions.isEmpty()) {
            actions = List.of(WorkflowNotificationResolverService.ResolvedNotificationAction.builder()
                    .channel("EMAIL")
                    .templateCode(code)
                    .eventType("ESIGN_LINK")
                    .recipientType("BORROWER_EMAIL")
                    .delaySeconds(0)
                    .recipients(to)
                    .build());
        }
        log.info("[ESIGN_EMAIL] trigger started applicationId={} applicationNumber={} templateCode={} documentType={} documentLabel={} recipientCount={} recipients={} reusedSigningUrl={} actionCount={}",
                applicationId, maskAppNumber(applicationNumber),
                code, documentType, templateData.get("documentLabel"), to.size(),
                maskEmails(to),
                reusedSigningUrl,
                actions.size());

        int published = 0;
        for (var action : actions) {
            // Stable binding: notification.email.# — include document key for multi-doc routing uniqueness only.
            String docSuffix = documentType != null && !documentType.isBlank()
                    ? "." + documentType.trim().toLowerCase().replace(' ', '_')
                    : "";
            String routingKey = "notification.email.esign_link" + docSuffix;
            for (String recipient : action.getRecipients()) {
                RoutingEmailEvent ev = RoutingEmailEvent.builder()
                        .channel(action.getChannel())
                        .recipient(recipient)
                        .templateCode(action.getTemplateCode())
                        .eventType(action.getEventType())
                        .applicationId(applicationId)
                        .templateData(new LinkedHashMap<>(templateData))
                        .build();
                try {
                    log.info("[ESIGN_NOTIFICATION_PUBLISH] templateCode={} eventType={} channel={} recipient={} applicationId={} routingKey={} documentType={} esignLinkPresent={}",
                            ev.getTemplateCode(),
                            ev.getEventType(),
                            ev.getChannel(),
                            recipient,
                            ev.getApplicationId(),
                            routingKey,
                            documentType,
                            templateData.get("esignLink") != null);
                    rabbitTemplate.convertAndSend(exchange, routingKey, ev);
                    published++;
                    log.info("[ESIGN_NOTIFICATION] queued exchange={} routingKey={} applicationId={} recipient={} documentType={}",
                            exchange, routingKey, applicationId, maskEmail(recipient), documentType);
                } catch (Exception e) {
                    log.error("[ESIGN_EMAIL][ERROR] RabbitMQ routing failed exchange={} routingKey={} applicationId={} recipient={} documentType={}: {}",
                            exchange, routingKey, applicationId, maskEmail(recipient), documentType, e.getMessage(), e);
                }
            }
        }
        log.info("[ESIGN_EMAIL] publish batch finished applicationId={} documentType={} publishedCount={} configuredActionCount={}",
                applicationId, documentType, published, actions.size());
    }

    private static String maskAppNumber(String applicationNumber) {
        if (applicationNumber == null || applicationNumber.length() <= 6) {
            return applicationNumber != null ? applicationNumber : "";
        }
        return applicationNumber.substring(0, 3) + "…" + applicationNumber.substring(applicationNumber.length() - 3);
    }

    private static String maskEmails(List<String> emails) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < emails.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(maskEmail(emails.get(i)));
        }
        return sb.toString();
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "*@" + (at < 0 ? "" : email.substring(at + 1));
        }
        return email.charAt(0) + "***@" + email.substring(at + 1);
    }

    @Data
    @Builder
    private static final class RoutingEmailEvent {
        private String channel;
        private String recipient;
        private String templateCode;
        private String eventType;
        private Map<String, Object> templateData;
        private UUID applicationId;
    }
}
