package com.iems.notificationservice.service;

import com.iems.notificationservice.dto.CreateNotificationRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

/**
 * Sends HTML email notifications using Spring Mail + Thymeleaf.
 * All send calls are @Async – fire-and-forget, never throws to caller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final com.iems.notificationservice.client.UserClient userClient;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    // ── Public API ────────────────────────────────────────────────

    /**
     * Gửi email dựa trên CreateNotificationRequest.
     * recipientEmail phải có sẵn trong request (được set bởi project-service).
     */
    @Async
    public void sendNotificationEmail(com.iems.notificationservice.dto.CreateNotificationRequest req,
                                      String recipientEmail,
                                      String recipientName) {
        if (!emailEnabled) {
            log.debug("Email sending disabled. Skipping email to {}", recipientEmail);
            return;
        }

        // Kiểm tra preferences của user
        if (req.getRecipientId() != null) {
            try {
                java.util.UUID recipientId = java.util.UUID.fromString(req.getRecipientId());
                var response = userClient.getNotificationPreferences(recipientId);
                if (response != null && response.getData() != null) {
                    java.util.Map<String, Object> prefs = response.getData();
                    boolean shouldSend = switch (req.getType()) {
                        case "ISSUE_ASSIGNED"   -> (boolean) prefs.getOrDefault("emailAssigned", true);
                        case "MEMBER_ADDED"     -> (boolean) prefs.getOrDefault("emailMemberAdded", true);
                        case "ISSUE_DUE_SOON"   -> (boolean) prefs.getOrDefault("emailDueSoon", true);
                        default                 -> true;
                    };
                    if (!shouldSend) {
                        log.info("User {} disabled email for type {}. Skipping.", recipientEmail, req.getType());
                        return;
                    }
                }
            } catch (Exception e) {
                log.warn("Could not fetch preferences for recipient {}: {}. Defaulting to send.", req.getRecipientId(), e.getMessage());
            }
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.debug("No recipient email for notification type={}, recipientId={}",
                    req.getType(), req.getRecipientId());
            return;
        }

        try {
            String subject  = buildSubject(req);
            String htmlBody = buildHtmlBody(req, recipientName);
            sendHtml(recipientEmail, subject, htmlBody);
            log.info("Email sent → {} | type={}", recipientEmail, req.getType());
        } catch (Exception e) {
            log.warn("Failed to send email to {} for type={}: {}", recipientEmail, req.getType(), e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────

    private void sendHtml(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
    }

    private String buildSubject(CreateNotificationRequest req) {
        return switch (req.getType()) {
            case "ISSUE_ASSIGNED"   -> "[IEMS] You were assigned: " + safe(req.getTitle());
            case "MEMBER_ADDED"     -> "[IEMS] You've been added to \"" + safe(req.getProjectName()) + "\"";
            case "ISSUE_DUE_SOON"   -> "[IEMS] ⚠️ Due tomorrow: " + safe(req.getTitle());
            case "ISSUE_COMMENTED"  -> "[IEMS] New comment on " + safe(req.getTitle());
            case "SPRINT_STARTED"   -> "[IEMS] Sprint started — " + safe(req.getProjectName());
            case "SPRINT_COMPLETED" -> "[IEMS] Sprint completed — " + safe(req.getProjectName());
            default                 -> "[IEMS] " + safe(req.getTitle());
        };
    }

    private String buildHtmlBody(CreateNotificationRequest req, String recipientName) {
        Context ctx = new Context(Locale.ENGLISH);

        ctx.setVariable("subject",          buildSubject(req));
        ctx.setVariable("recipientName",    recipientName != null && !recipientName.isBlank() ? recipientName : "there");
        ctx.setVariable("body",             safe(req.getBody()));
        ctx.setVariable("notificationType", safe(req.getType()));
        ctx.setVariable("badgeLabel",       buildBadgeLabel(req.getType()));
        ctx.setVariable("projectName",      req.getProjectName());
        ctx.setVariable("actorName",        req.getActorName());

        // Accent color theo loại notification
        String[] colors = resolveAccentColors(req.getType());
        ctx.setVariable("accentColor",      colors[0]);
        ctx.setVariable("accentColorDark",  colors[1]);

        // Entity label row
        ctx.setVariable("entityLabelKey",   resolveEntityLabelKey(req.getType()));
        ctx.setVariable("entityLabel",      resolveEntityLabel(req));

        // CTA
        ctx.setVariable("linkUrl",          buildLink(req));
        ctx.setVariable("ctaLabel",         buildCtaLabel(req.getType()));

        return templateEngine.process("email/notification-email", ctx);
    }

    private String buildBadgeLabel(String type) {
        return switch (type) {
            case "ISSUE_ASSIGNED"   -> "✅  Task Assigned";
            case "MEMBER_ADDED"     -> "🎉  Added to Project";
            case "ISSUE_DUE_SOON"   -> "⚠️  Due Tomorrow";
            case "ISSUE_COMMENTED"  -> "💬  New Comment";
            case "SPRINT_STARTED"   -> "🚀  Sprint Started";
            case "SPRINT_COMPLETED" -> "🏁  Sprint Completed";
            default                 -> "🔔  Notification";
        };
    }

    /** [primary, dark] gradient colors per notification type */
    private String[] resolveAccentColors(String type) {
        return switch (type) {
            case "ISSUE_ASSIGNED"   -> new String[]{"#4f46e5", "#3730a3"};
            case "MEMBER_ADDED"     -> new String[]{"#059669", "#047857"};
            case "ISSUE_DUE_SOON"   -> new String[]{"#d97706", "#b45309"};
            case "ISSUE_COMMENTED"  -> new String[]{"#2563eb", "#1d4ed8"};
            case "SPRINT_STARTED"   -> new String[]{"#7c3aed", "#6d28d9"};
            case "SPRINT_COMPLETED" -> new String[]{"#db2777", "#be185d"};
            default                 -> new String[]{"#6366f1", "#4f46e5"};
        };
    }

    private String resolveEntityLabelKey(String type) {
        return switch (type) {
            case "ISSUE_ASSIGNED", "ISSUE_DUE_SOON", "ISSUE_COMMENTED" -> "Task";
            case "SPRINT_STARTED", "SPRINT_COMPLETED"                   -> "Sprint";
            case "MEMBER_ADDED"                                          -> "Project";
            default                                                      -> "Item";
        };
    }

    private String resolveEntityLabel(CreateNotificationRequest req) {
        if (req.getTitle() != null && !req.getTitle().isBlank()) return req.getTitle();
        return req.getEntityId();
    }

    private String buildLink(CreateNotificationRequest req) {
        if (req.getLinkPath() == null || req.getLinkPath().isBlank()) return null;
        // frontendUrl được load từ ${app.frontend-url} trong application.properties
        // → set qua env FRONTEND_URL
        String base = (frontendUrl != null && !frontendUrl.isBlank())
                ? frontendUrl.replaceAll("/$", "")
                : "http://localhost:3000";
        return base + req.getLinkPath();
    }

    private String buildCtaLabel(String type) {
        return switch (type) {
            case "ISSUE_ASSIGNED", "ISSUE_DUE_SOON", "ISSUE_COMMENTED" -> "View Task →";
            case "MEMBER_ADDED"     -> "Go to Project →";
            case "SPRINT_STARTED"   -> "View Board →";
            case "SPRINT_COMPLETED" -> "View Sprint →";
            default                 -> "View Details →";
        };
    }

    private String safe(String s) { return s != null ? s : ""; }
}
