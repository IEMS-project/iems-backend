package com.iems.iamservice.service;

import com.iems.iamservice.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionSchedulerServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private SubscriptionSchedulerService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionSchedulerService(accountRepository);
    }

    @Test
    void autoDowngradeExpiredAccountsShouldBulkDowngradeExpiredPremiumAccounts() {
        Instant beforeRun = Instant.now();
        when(accountRepository.downgradeExpiredPremiumAccounts(argThat(now -> !now.isBefore(beforeRun))))
                .thenReturn(2);

        service.autoDowngradeExpiredAccounts();

        verify(accountRepository).downgradeExpiredPremiumAccounts(argThat(now -> !now.isBefore(beforeRun)));
    }
}
