package com.iems.iamservice.service;

import com.iems.iamservice.entity.Account;
import com.iems.iamservice.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStatusCacheServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountStatusCacheService service;

    @BeforeEach
    void setUp() {
        service = new AccountStatusCacheService(accountRepository);
        ReflectionTestUtils.setField(service, "ttlSeconds", 3600L);
    }

    @Test
    void isEnabledShouldReturnFalseForNullAccountId() {
        assertFalse(service.isEnabled(null));
    }

    @Test
    void isEnabledShouldLoadAndCacheRepositoryValue() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).enabled(true).build();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertTrue(service.isEnabled(accountId));
        assertTrue(service.isEnabled(accountId));
        verify(accountRepository).findById(accountId);
    }

    @Test
    void updateShouldOverrideCachedValue() {
        UUID accountId = UUID.randomUUID();
        service.update(accountId, false);

        assertFalse(service.isEnabled(accountId));
        verify(accountRepository, never()).findById(accountId);
    }

    @Test
    void evictShouldForceReload() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).enabled(true).build();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        service.update(accountId, false);
        service.evict(accountId);

        assertTrue(service.isEnabled(accountId));
        verify(accountRepository).findById(accountId);
    }
}