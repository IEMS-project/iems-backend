package com.iems.iamservice.service;

import com.iems.iamservice.entity.Account;
import com.iems.iamservice.entity.enums.UserRole;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRolePermissionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private UserRolePermissionService service;

    @BeforeEach
    void setUp() {
        service = new UserRolePermissionService(accountRepository);
    }

    @Test
    void assignRolesToUserShouldPersistSingleRole() {
        UUID userId = UUID.randomUUID();
        Account account = Account.builder().id(userId).build();
        when(accountRepository.findById(userId)).thenReturn(Optional.of(account));

        service.assignRolesToUser(userId, Set.of("admin"));

        assertEquals(UserRole.ADMIN, account.getRole());
        verify(accountRepository).save(account);
    }

    @Test
    void assignRolesToUserShouldRejectMultipleRoles() {
        assertThrows(AppException.class, () -> service.assignRolesToUser(UUID.randomUUID(), Set.of("admin", "user")));
    }

    @Test
    void getUserRolesShouldReturnEmptyWhenNoRole() {
        UUID userId = UUID.randomUUID();
        when(accountRepository.findById(userId)).thenReturn(Optional.of(Account.builder().id(userId).build()));

        assertEquals(Set.of("USER"), service.getUserRoles(userId));
    }

    @Test
    void getUserIdsByRoleCodesShouldIgnoreInvalidRoles() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Account admin = Account.builder().id(adminId).role(UserRole.ADMIN).build();
        Account user = Account.builder().id(userId).role(UserRole.USER).build();
        when(accountRepository.findAll()).thenReturn(List.of(admin, user));

        assertEquals(List.of(adminId), service.getUserIdsByRoleCodes(List.of("admin", "missing")));
    }

    @Test
    void removeRoleFromUserShouldClearMatchingRole() {
        UUID userId = UUID.randomUUID();
        Account account = Account.builder().id(userId).role(UserRole.ADMIN).build();
        when(accountRepository.findById(userId)).thenReturn(Optional.of(account));

        service.removeRoleFromUser(userId, "admin");

        assertNull(account.getRole());
        verify(accountRepository).save(account);
    }
}