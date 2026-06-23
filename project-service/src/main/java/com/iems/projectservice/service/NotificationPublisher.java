package com.iems.projectservice.service;

import com.iems.projectservice.client.NotificationFeignClient;
import com.iems.projectservice.dto.request.CreateNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fire-and-forget notification publisher.
 *
 * QUAN TRỌNG: Email/name của recipient phải được resolve TRÊN REQUEST THREAD
 * (trước khi @Async handoff), vì FeignClientConfig.requestInterceptor() cần
 * RequestContextHolder (HTTP request context) để forward Authorization header.
 * Trong @Async thread, request context đã bị mất → Feign call thất bại.
 *
 * Pattern: mỗi notify*() method resolve email trên calling thread,
 * rồi mới gọi sendAsync() / sendBatchAsync() để fire-and-forget.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisher {

    private static final String INTERNAL_SECRET = "iems-internal-2025";

    private final NotificationFeignClient notificationFeignClient;
    private final UserEmailResolver userEmailResolver;

    // ── Internal async senders (KHÔNG resolve email ở đây) ──────────

    @Async
    public void sendAsync(CreateNotificationRequest request) {
        if (request.getRecipientId() == null) return;
        try {
            notificationFeignClient.sendNotification(INTERNAL_SECRET, request);
        } catch (Exception e) {
            log.warn("Failed to send notification to user {}: {}", request.getRecipientId(), e.getMessage());
        }
    }

    @Async
    public void sendBatchAsync(List<CreateNotificationRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        try {
            notificationFeignClient.sendNotifications(INTERNAL_SECRET, requests);
        } catch (Exception e) {
            log.warn("Failed to send batch notifications (count={}): {}", requests.size(), e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String actorDisplay(String actorName) {
        return (actorName != null && !actorName.isBlank()) ? actorName : "Someone";
    }

    /** Resolve email + name trên REQUEST thread rồi attach vào request */
    private void attachRecipientInfo(CreateNotificationRequest req) {
        if (req.getRecipientId() == null || req.getRecipientEmail() != null) return;
        try {
            UserEmailResolver.UserInfo info = userEmailResolver.resolve(req.getRecipientId());
            req.setRecipientEmail(info.email());
            if (req.getRecipientName() == null) req.setRecipientName(info.displayName());
        } catch (Exception e) {
            log.debug("Could not resolve recipient info for {}: {}", req.getRecipientId(), e.getMessage());
        }
    }

    /** Batch resolve email + name trên REQUEST thread */
    private void attachRecipientInfoBatch(List<CreateNotificationRequest> requests) {
        Set<UUID> needsLookup = requests.stream()
                .filter(r -> r.getRecipientId() != null && r.getRecipientEmail() == null)
                .map(CreateNotificationRequest::getRecipientId)
                .collect(Collectors.toSet());

        if (needsLookup.isEmpty()) return;

        try {
            Map<UUID, UserEmailResolver.UserInfo> emailMap = userEmailResolver.resolveAll(needsLookup);
            for (CreateNotificationRequest req : requests) {
                if (req.getRecipientId() != null && req.getRecipientEmail() == null) {
                    UserEmailResolver.UserInfo info = emailMap.get(req.getRecipientId());
                    if (info != null) {
                        req.setRecipientEmail(info.email());
                        if (req.getRecipientName() == null) req.setRecipientName(info.displayName());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not batch-resolve recipient info: {}", e.getMessage());
        }
    }

    // ── Typed factory methods (gọi trên REQUEST thread) ──────────────

    public void notifyIssueAssigned(UUID recipientId, UUID actorId, String actorName,
                                     String issueKey, String issueTitle, UUID issueId,
                                     UUID projectId, String projectName) {
        if (recipientId == null || recipientId.equals(actorId)) return;
        CreateNotificationRequest req = CreateNotificationRequest.builder()
                .recipientId(recipientId)
                .actorId(actorId)
                .actorName(actorName)
                .type("ISSUE_ASSIGNED")
                .title(issueKey + ": " + issueTitle)
                .body(actorDisplay(actorName) + " assigned you to " + issueKey + ": " + issueTitle)
                .entityId(issueId.toString())
                .entityType("ISSUE")
                .projectId(projectId)
                .projectName(projectName)
                .linkPath("/projects/" + projectId + "/backlog")
                .build();
        attachRecipientInfo(req);   // resolve trên request thread
        sendAsync(req);
    }

    public void notifyIssueCommented(UUID recipientId, UUID actorId, String actorName,
                                      String issueKey, UUID issueId,
                                      UUID projectId, String projectName) {
        if (recipientId == null || recipientId.equals(actorId)) return;
        CreateNotificationRequest req = CreateNotificationRequest.builder()
                .recipientId(recipientId)
                .actorId(actorId)
                .actorName(actorName)
                .type("ISSUE_COMMENTED")
                .title("New comment on " + issueKey)
                .body(actorDisplay(actorName) + " commented on " + issueKey)
                .entityId(issueId.toString())
                .entityType("ISSUE")
                .projectId(projectId)
                .projectName(projectName)
                .linkPath("/projects/" + projectId + "/backlog")
                .build();
        attachRecipientInfo(req);
        sendAsync(req);
    }

    public void notifyMemberAdded(UUID recipientId, UUID actorId, String actorName,
                                   UUID projectId, String projectName) {
        if (recipientId == null || recipientId.equals(actorId)) return;
        CreateNotificationRequest req = CreateNotificationRequest.builder()
                .recipientId(recipientId)
                .actorId(actorId)
                .actorName(actorName)
                .type("MEMBER_ADDED")
                .title("Added to project \"" + projectName + "\"")
                .body(actorDisplay(actorName) + " added you to project \"" + projectName + "\"")
                .entityId(projectId.toString())
                .entityType("PROJECT")
                .projectId(projectId)
                .projectName(projectName)
                .linkPath("/projects/" + projectId + "/overview")
                .build();
        attachRecipientInfo(req);   // ← đây là fix chính: resolve trước @Async
        sendAsync(req);
    }

    public void notifySprintStarted(List<UUID> memberIds, UUID actorId, String actorName,
                                     UUID sprintId, String sprintName,
                                     UUID projectId, String projectName) {
        List<CreateNotificationRequest> requests = memberIds.stream()
                .filter(id -> id != null && !id.equals(actorId))
                .map(id -> CreateNotificationRequest.builder()
                        .recipientId(id)
                        .actorId(actorId)
                        .actorName(actorName)
                        .type("SPRINT_STARTED")
                        .title("Sprint \"" + sprintName + "\" started")
                        .body("Sprint \"" + sprintName + "\" has started in \"" + projectName + "\"")
                        .entityId(sprintId.toString())
                        .entityType("SPRINT")
                        .projectId(projectId)
                        .projectName(projectName)
                        .linkPath("/projects/" + projectId + "/board")
                        .build())
                .collect(Collectors.toList());
        attachRecipientInfoBatch(requests);  // resolve trước @Async
        sendBatchAsync(requests);
    }

    public void notifySprintCompleted(List<UUID> memberIds, UUID actorId, String actorName,
                                       UUID sprintId, String sprintName,
                                       UUID projectId, String projectName) {
        List<CreateNotificationRequest> requests = memberIds.stream()
                .filter(id -> id != null && !id.equals(actorId))
                .map(id -> CreateNotificationRequest.builder()
                        .recipientId(id)
                        .actorId(actorId)
                        .actorName(actorName)
                        .type("SPRINT_COMPLETED")
                        .title("Sprint \"" + sprintName + "\" completed")
                        .body("Sprint \"" + sprintName + "\" has been completed in \"" + projectName + "\"")
                        .entityId(sprintId.toString())
                        .entityType("SPRINT")
                        .projectId(projectId)
                        .projectName(projectName)
                        .linkPath("/projects/" + projectId + "/sprints")
                        .build())
                .collect(Collectors.toList());
        attachRecipientInfoBatch(requests);
        sendBatchAsync(requests);
    }

    public void notifyIssueDueSoon(UUID recipientId, String issueKey, String issueTitle,
                                    UUID issueId, UUID projectId, String projectName,
                                    String recipientEmail, String recipientName) {
        if (recipientId == null) return;
        CreateNotificationRequest req = CreateNotificationRequest.builder()
                .recipientId(recipientId)
                .type("ISSUE_DUE_SOON")
                .title(issueKey + ": " + issueTitle)
                .body("Task " + issueKey + " \"" + issueTitle + "\" is due tomorrow in project \"" + projectName + "\"")
                .entityId(issueId.toString())
                .entityType("ISSUE")
                .projectId(projectId)
                .projectName(projectName)
                .linkPath("/projects/" + projectId + "/backlog")
                .recipientEmail(recipientEmail)
                .recipientName(recipientName)
                .build();
        sendAsync(req);
    }

    public void notifyCommentReplied(UUID recipientId, UUID actorId, String actorName,
                                     String issueKey, UUID issueId,
                                     UUID commentId, UUID projectId, String projectName) {
        if (recipientId == null || recipientId.equals(actorId)) return;
        CreateNotificationRequest req = CreateNotificationRequest.builder()
                .recipientId(recipientId)
                .actorId(actorId)
                .actorName(actorName)
                .type("COMMENT_REPLIED")
                .title("Reply on your comment in " + issueKey)
                .body(actorDisplay(actorName) + " replied to your comment in " + issueKey)
                .entityId(issueId.toString())
                .entityType("ISSUE")
                .projectId(projectId)
                .projectName(projectName)
                .linkPath("/projects/" + projectId + "/backlog?issueId=" + issueId + "&commentId=" + commentId)
                .build();
        attachRecipientInfo(req);
        sendAsync(req);
    }

    public void notifyMentioned(List<UUID> recipientIds, UUID actorId, String actorName,
                                 String issueKey, String issueTitle, UUID issueId,
                                 UUID commentId, UUID projectId, String projectName) {
        if (recipientIds == null || recipientIds.isEmpty()) return;
        List<CreateNotificationRequest> requests = recipientIds.stream()
                .filter(id -> id != null && !id.equals(actorId))
                .distinct()
                .map(id -> CreateNotificationRequest.builder()
                        .recipientId(id)
                        .actorId(actorId)
                        .actorName(actorName)
                        .type("ISSUE_MENTIONED")
                        .title("Mentioned in " + issueKey)
                        .body(actorDisplay(actorName) + " mentioned you in " + issueKey + ": " + issueTitle)
                        .entityId(issueId.toString())
                        .entityType("ISSUE")
                        .projectId(projectId)
                        .projectName(projectName)
                        .linkPath("/projects/" + projectId + "/backlog?issueId=" + issueId + "&commentId=" + commentId)
                        .build())
                .collect(Collectors.toList());
        attachRecipientInfoBatch(requests);
        sendBatchAsync(requests);
    }
}
