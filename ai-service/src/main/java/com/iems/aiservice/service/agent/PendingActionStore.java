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

    /**
     * Saves pending action data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param action the action parameter
     */
    public void save(PendingAgentAction action) {
        if (action == null) {
            return;
        }
        pendingActions.put(key(action.conversationId(), action.userId()), action);
    }

    /**
     * Finds pending action information that matches the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param userId the user id parameter
     * @return an optional result when matching data is available
     */
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

    /**
     * Returns consume for pending action processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param userId the user id parameter
     * @return an optional result when matching data is available
     */
    public Optional<PendingAgentAction> consume(String conversationId, String userId) {
        String key = key(conversationId, userId);
        PendingAgentAction action = pendingActions.remove(key);
        if (action == null || action.isExpired(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(action);
    }

    /**
     * Clears pending action state.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     */
    public void clear() {
        pendingActions.clear();
    }

    /**
     * Returns key for pending action processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param userId the user id parameter
     * @return the key result
     */
    private static String key(String conversationId, String userId) {
        return safe(conversationId) + ":" + safe(userId);
    }

    /**
     * Returns safe for pending action processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the safe result
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
