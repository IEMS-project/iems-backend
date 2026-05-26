package com.iems.iamservice.service;

import com.iems.iamservice.entity.Account;
import com.iems.iamservice.entity.enums.UserRole;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
import com.iems.iamservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRolePermissionService {

    private final AccountRepository accountRepository;


    /**
     * Assign role to user (single role only - replaces existing role)
     */
    @Transactional
    public void assignRolesToUser(UUID userId, Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            log.info("No role codes provided for userId={}, skipping assignment", userId);
            return;
        }
        
        // Validate: only 1 role allowed per user
        if (roleCodes.size() > 1) {
            throw new AppException(ErrorCode.MULTIPLE_ROLES_NOT_ALLOWED);
        }
        
        String roleCode = roleCodes.iterator().next();
        log.info("Assigning role {} to user ID: {}", roleCode, userId);

        // Parse role from string
        UserRole role;
        try {
            role = UserRole.valueOf(roleCode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.ROLE_NOT_FOUND_BY_CODE);
        }

        // Get the account and update role
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND_BY_ID));
        
        account.setRole(role);
        accountRepository.save(account);
        
        log.info("Role {} assigned to user ID: {}", roleCode, userId);
    }

    /**
     * Get role assigned to user (returns Set for backward compatibility)
     */
    public Set<String> getUserRoles(UUID userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND_BY_ID));
        
        if (account.getRole() != null) {
            return Set.of(account.getRole().name());
        }
        return Set.of();
    }

    /**
     * Get all user IDs that have any of the specified roles
     */
    public List<UUID> getUserIdsByRoleCodes(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }
        
        Set<UserRole> roles = roleCodes.stream()
                .map(code -> {
                    try {
                        return UserRole.valueOf(code.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(role -> role != null)
                .collect(Collectors.toSet());
        
        return accountRepository.findAll().stream()
                .filter(account -> account.getRole() != null && roles.contains(account.getRole()))
                .map(Account::getId)
                .collect(Collectors.toList());
    }

    /**
     * Check if user has specific role
     */
    public boolean userHasRole(UUID userId, String roleCode) {
        Account account = accountRepository.findById(userId).orElse(null);
        if (account == null || account.getRole() == null) {
            return false;
        }
        return account.getRole().name().equalsIgnoreCase(roleCode);
    }

    /**
     * Remove role from user
     */
    @Transactional
    public void removeRoleFromUser(UUID userId, String roleCode) {
        log.info("Removing role {} from user ID: {}", roleCode, userId);

        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND_BY_ID));

        if (account.getRole() != null && account.getRole().name().equalsIgnoreCase(roleCode)) {
            account.setRole(null);
            accountRepository.save(account);
            log.info("Role removed successfully from user ID: {}", userId);
        } else {
            log.warn("User {} does not have role {}", userId, roleCode);
        }
    }

    /**
     * Get all users assigned to a role
     */
    public List<UUID> getUsersByRole(String roleCode) {
        UserRole role;
        try {
            role = UserRole.valueOf(roleCode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.ROLE_NOT_FOUND_BY_CODE);
        }
        
        return accountRepository.findAll().stream()
                .filter(account -> account.getRole() == role)
                .map(Account::getId)
                .collect(Collectors.toList());
    }
}
