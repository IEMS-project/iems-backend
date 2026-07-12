package com.iems.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry quản lý SSE emitters theo userId.
 * Thread-safe, hỗ trợ nhiều tab/device cùng lúc.
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    // userId → emitter (1 emitter per user, last-one-wins nếu login nhiều tab)
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Tạo và đăng ký emitter mới cho userId.
     * Timeout 5 phút — FE sẽ reconnect sau đó.
     */
    public SseEmitter createEmitter(UUID userId) {
        // Timeout 5 phút
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        emitter.onCompletion(() -> {
            emitters.remove(userId);
            log.debug("SSE emitter completed for user {}", userId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(userId);
            log.debug("SSE emitter timed out for user {}", userId);
        });
        emitter.onError(e -> {
            emitters.remove(userId);
            log.debug("SSE emitter error for user {}: {}", userId, e.getMessage());
        });

        // Nếu user đã có emitter cũ (tab khác), close nó trước
        SseEmitter old = emitters.put(userId, emitter);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) {}
        }

        // Gửi comment để browser biết connection sống
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE comment to user {}", userId);
        }

        return emitter;
    }

    /**
     * Push notification tới user nếu đang online.
     * @return true nếu gửi thành công
     */
    public boolean push(UUID userId, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return false;

        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(data));
            return true;
        } catch (IOException e) {
            log.debug("Failed to push SSE to user {}, removing emitter", userId);
            emitters.remove(userId);
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
            return false;
        }
    }

    /**
     * Returns is online for sse emitter processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    public boolean isOnline(UUID userId) {
        return emitters.containsKey(userId);
    }

    /**
     * Retrieves sse emitter information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the get online count result
     */
    public int getOnlineCount() {
        return emitters.size();
    }
}
