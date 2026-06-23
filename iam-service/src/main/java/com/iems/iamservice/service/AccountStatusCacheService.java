package com.iems.iamservice.service;

import com.iems.iamservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusCacheService {

    private final AccountRepository accountRepository;
    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${iam.account-status-cache.ttl-seconds:30}")
    private long ttlSeconds;

    public boolean isEnabled(UUID accountId) {
        if (accountId == null) {
            return false;
        }

        CacheEntry cached = cache.get(accountId);
        if (cached != null && !cached.isExpired(ttlSeconds)) {
            return cached.enabled();
        }

        try {
            boolean enabled = accountRepository.findById(accountId)
                    .map(account -> Boolean.TRUE.equals(account.getEnabled()))
                    .orElse(false);
            cache.put(accountId, new CacheEntry(enabled, Instant.now()));
            return enabled;
        } catch (Exception ex) {
            log.warn("Failed to load account status for {}: {}", accountId, ex.getMessage());
            return false;
        }
    }

    public void update(UUID accountId, boolean enabled) {
        if (accountId != null) {
            cache.put(accountId, new CacheEntry(enabled, Instant.now()));
        }
    }

    public void evict(UUID accountId) {
        if (accountId != null) {
            cache.remove(accountId);
        }
    }

    private record CacheEntry(boolean enabled, Instant loadedAt) {
        boolean isExpired(long ttlSeconds) {
            return loadedAt.plus(Duration.ofSeconds(ttlSeconds)).isBefore(Instant.now());
        }
    }
}
