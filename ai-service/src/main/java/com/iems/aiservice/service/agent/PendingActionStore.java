package com.iems.aiservice.service.agent;

import com.iems.aiservice.model.agent.PendingAgentAction;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class PendingActionStore {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private final ConcurrentMap<String, PendingAgentAction> pendingActions = new ConcurrentHashMap<>();

    public void save(PendingAgentAction action) {
        if (action == null) {
            return;
        }
        pendingActions.put(key(action.conversationId(), action.userId()), action);
    }

    public Optional<PendingAgentAction> find(String conversationId, String userId) {
        String key = key(conversationId, userId);
        PendingAgentAction action = pendingActions.get(key);
        if (action == null) {
            return Optional.empty();
        }
        if (action.isExpired(Instant.now())) {
            pendingActions.remove(key);
            return Optional.empty();
        }
        return Optional.of(action);
    }

    public Optional<PendingAgentAction> consume(String conversationId, String userId) {
        String key = key(conversationId, userId);
        PendingAgentAction action = pendingActions.remove(key);
        if (action == null || action.isExpired(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(action);
    }

    public void clear() {
        pendingActions.clear();
    }

    private static String key(String conversationId, String userId) {
        return safe(conversationId) + ":" + safe(userId);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
