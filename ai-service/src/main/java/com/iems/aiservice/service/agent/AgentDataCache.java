package com.iems.aiservice.service.agent;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class AgentDataCache {

    public static final Duration PROJECT_ISSUES_TTL = Duration.ofSeconds(30);
    public static final Duration MY_ISSUES_TTL = Duration.ofSeconds(20);
    public static final Duration PROJECT_OVERVIEW_TTL = Duration.ofSeconds(30);
    public static final Duration STATIC_PROJECT_DATA_TTL = Duration.ofMinutes(10);

    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();

    /**
     * Retrieves agent data information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param namespace the namespace parameter
     * @param key the key parameter
     * @param ttl the ttl parameter
     * @param loader the loader parameter
     * @return the get or load result
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String namespace, String key, Duration ttl, Supplier<T> loader) {
        String cacheKey = cacheKey(namespace, key);
        Instant now = Instant.now();
        CacheEntry existing = entries.get(cacheKey);
        if (existing != null && existing.expiresAt().isAfter(now)) {
            return (T) existing.value();
        }

        T loaded = loader.get();
        if (loaded != null) {
            entries.put(cacheKey, new CacheEntry(copyForCache(loaded), now.plus(ttl)));
        }
        return loaded;
    }

    /**
     * Evicts cached agent data state.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     */
    public void evictProjectWriteData(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        String projectToken = ":" + projectId + ":";
        String projectSuffix = ":" + projectId;
        entries.keySet().removeIf(key -> key.contains(projectToken)
                || key.endsWith(projectSuffix)
                || key.startsWith("project_issues:" + projectId)
                || key.startsWith("project_overview:" + projectId)
                || key.startsWith("risk_signals:" + projectId)
                || key.startsWith("member_workload:" + projectId)
                || key.startsWith("my_issues:" + projectId + ":"));
    }

    /**
     * Clears agent data state.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     */
    public void clear() {
        entries.clear();
    }

    /**
     * Returns size for agent data processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the size result
     */
    int size() {
        return entries.size();
    }

    /**
     * Returns cache key for agent data processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param namespace the namespace parameter
     * @param key the key parameter
     * @return the cache key result
     */
    private static String cacheKey(String namespace, String key) {
        return Objects.toString(namespace, "default") + ":" + Objects.toString(key, "");
    }

    /**
     * Returns copy for cache for agent data processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the copy for cache result
     */
    private static Object copyForCache(Object value) {
        if (value instanceof java.util.List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(map);
        }
        return value;
    }

    private record CacheEntry(Object value, Instant expiresAt) {
    }
}
