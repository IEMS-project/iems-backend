package com.iems.notificationservice.controller;

import com.iems.notificationservice.dto.CreateNotificationRequest;
import com.iems.notificationservice.dto.NotificationDto;
import com.iems.notificationservice.service.NotificationService;
import com.iems.notificationservice.service.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterRegistry sseEmitterRegistry;

    // ── SSE Stream (user connects on login) ───────────────────────

    /**
     * FE gọi endpoint này để mở SSE connection.
     * Sau khi connect, server sẽ push notification realtime.
     */
    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = extractUserId(userDetails);
        log.debug("SSE connection opened for user {}", userId);
        return sseEmitterRegistry.createEmitter(userId);
    }

    // ── REST: List, count ─────────────────────────────────────────

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = extractUserId(userDetails);
        Page<NotificationDto> result = notificationService.getNotifications(userId, unreadOnly, page, size);
        long unreadCount = notificationService.countUnread(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("currentPage", page);
        response.put("unreadCount", unreadCount);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = extractUserId(userDetails);
        long count = notificationService.countUnread(userId);
        return ResponseEntity.ok(Map.of("status", "success", "data", count));
    }

    // ── REST: Mark read ───────────────────────────────────────────

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = extractUserId(userDetails);
        boolean updated = notificationService.markRead(id, userId);
        return ResponseEntity.ok(Map.of("status", "success", "updated", updated));
    }

    @PatchMapping("/notifications/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = extractUserId(userDetails);
        int count = notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("status", "success", "updated", count));
    }

    // ── Internal API: called by project-service ───────────────────

    /**
     * project-service gọi endpoint này (Feign) để tạo notification.
     * Không cần user JWT — dùng internal header để verify.
     */
    @PostMapping("/notifications/internal")
    public ResponseEntity<Map<String, Object>> createInternal(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody CreateNotificationRequest request) {
        // Simple shared-secret check (production nên dùng mTLS hoặc API key)
        if (!"iems-internal-2025".equals(secret)) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Forbidden"));
        }
        NotificationDto dto = notificationService.create(request);
        return ResponseEntity.ok(Map.of("status", "success", "data", dto));
    }

    @PostMapping("/notifications/internal/batch")
    public ResponseEntity<Map<String, Object>> createBatchInternal(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody List<CreateNotificationRequest> requests) {
        if (!"iems-internal-2025".equals(secret)) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Forbidden"));
        }
        notificationService.createBatch(requests);
        return ResponseEntity.ok(Map.of("status", "success", "count", requests.size()));
    }

    // ── Helper ────────────────────────────────────────────────────

    private UUID extractUserId(UserDetails userDetails) {
        // JwtUserDetails stores userId as username field (UUID string)
        return UUID.fromString(userDetails.getUsername());
    }
}
