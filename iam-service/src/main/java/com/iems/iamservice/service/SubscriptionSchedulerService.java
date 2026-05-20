package com.iems.iamservice.service;

import com.iems.iamservice.entity.Account;
import com.iems.iamservice.entity.enums.SubscriptionType;
import com.iems.iamservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled service for subscription lifecycle management:
 *  1. Daily: auto-downgrade accounts whose premiumUntil has passed.
 *  2. Daily: detect accounts expiring within 7 days and log a warning
 *             (or call notification service if available).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionSchedulerService {

    private final AccountRepository accountRepository;

    /**
     * Run daily at 01:00 to auto-downgrade expired PREMIUM accounts to FREE.
     * The project-service scheduler handles locking projects afterwards.
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void autoDowngradeExpiredAccounts() {
        log.info("[SubscriptionScheduler] Checking for expired premium accounts...");

        Instant now = Instant.now();
        List<Account> allPremium = accountRepository.findBySubscriptionType(SubscriptionType.PREMIUM);

        int downgraded = 0;
        for (Account account : allPremium) {
            if (account.getPremiumUntil() != null && account.getPremiumUntil().isBefore(now)) {
                account.setSubscriptionType(SubscriptionType.FREE);
                account.setPremiumUntil(null);
                accountRepository.save(account);
                downgraded++;
                log.warn("[SubscriptionScheduler] Auto-downgraded account {} ({}) to FREE – premium expired",
                        account.getId(), account.getUsername());
            }
        }

        log.info("[SubscriptionScheduler] Auto-downgrade complete. downgraded={}", downgraded);
    }

    /**
     * Run daily at 01:30 to warn accounts expiring within the next 7 days.
     * Currently logs a warning; integrate with notification-service as needed.
     */
    @Scheduled(cron = "0 30 1 * * *")
    public void warnExpiringAccounts() {
        log.info("[SubscriptionScheduler] Checking for soon-to-expire premium accounts...");

        Instant now = Instant.now();
        Instant sevenDaysLater = now.plus(7, ChronoUnit.DAYS);

        List<Account> allPremium = accountRepository.findBySubscriptionType(SubscriptionType.PREMIUM);

        for (Account account : allPremium) {
            if (account.getPremiumUntil() != null
                    && account.getPremiumUntil().isAfter(now)
                    && account.getPremiumUntil().isBefore(sevenDaysLater)) {
                // TODO: call notification-service to send in-app / email warning
                log.info("[SubscriptionScheduler] Account {} ({}) premium expires soon at {}",
                        account.getId(), account.getUsername(), account.getPremiumUntil());
            }
        }
    }
}
