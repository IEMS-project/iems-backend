package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.CreateAccountDto;
import com.iems.iamservice.dto.request.UpdateAccountDto;
import com.iems.iamservice.dto.response.AccountResponseDto;
import com.iems.iamservice.entity.Account;


import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.entity.Role;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
import com.iems.iamservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private UserRolePermissionService userRolePermissionService;



    /**
     * Create new user
     */
    @Transactional
    public Account createUser(CreateAccountDto dto) {
        log.info("Creating new user: {}", dto.getUsername());

        if (accountRepository.existsByUsername(dto.getUsername())) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (accountRepository.existsByEmail(dto.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Account user = Account.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        Account savedUser = accountRepository.save(user);
        
        // Assign roles after saving account to get the ID
        if (dto.getRoleCodes() != null && !dto.getRoleCodes().isEmpty()) {
            userRolePermissionService.assignRolesToUser(savedUser.getId(), dto.getRoleCodes());
        }
        
        log.info("User created successfully: {} with ID: {}", savedUser.getUsername(), savedUser.getId());
        return savedUser;
    }

    /**
     * Get all users
     */
    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    /**
     * Find user by ID
     */
    public Account findById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND_BY_ID));
    }

    /**
     * Find user by username or email
     */
    public Optional<Account> findByUsernameOrEmail(String usernameOrEmail) {
        return accountRepository.findByUsernameOrEmail(usernameOrEmail);
    }

    /**
     * Find user by userId (deprecated - for backward compatibility)
     */
    @Deprecated
    public Optional<Account> findByUserId(UUID userId) {
        return accountRepository.findById(userId);
    }

    /**
     * Update user
     */
    @Transactional
    public Account update(UUID id, UpdateAccountDto dto) {
        log.info("Updating user with ID: {}", id);

        Account user = findById(id);
        
        if (dto.getEmail() != null && !dto.getEmail().equals(user.getEmail())) {
            if (accountRepository.existsByEmail(dto.getEmail())) {
                throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            user.setEmail(dto.getEmail());
        }
        
        if (dto.getEnabled() != null) {
            user.setEnabled(dto.getEnabled());
        }
        

        Account updatedUser = accountRepository.save(user);
        log.info("User updated successfully: {}", updatedUser.getUsername());
        return updatedUser;
    }

    /**
     * Reset user password
     */
    @Transactional
    public Account resetPassword(UUID id, String newPassword) {
        log.info("Resetting password for user with ID: {}", id);
        try {
            Account user = findById(id);
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            Account updatedUser = accountRepository.save(user);
            log.info("Password reset successfully for user: {}", updatedUser.getUsername());
            return updatedUser;
        } catch (Exception e) {
            log.error("Failed to reset password for user with ID: {}", id, e);
            throw new AppException(ErrorCode.USER_UPDATE_FAILED);
        }
    }

    /**
     * Lock/Unlock user
     */
    @Transactional
    public Account lockUser(UUID id, boolean locked, String reason) {
        log.info("{} user with ID: {}, reason: {}", locked ? "Locking" : "Unlocking", id, reason);

        try {
            Account user = findById(id);
            user.setEnabled(!locked);
            
            Account updatedUser = accountRepository.save(user);
            log.info("User {} successfully: {}", locked ? "locked" : "unlocked", updatedUser.getUsername());
            return updatedUser;
        } catch (Exception e) {
            log.error("Failed to {} user with ID: {}", locked ? "lock" : "unlock", id, e);
            throw new AppException(ErrorCode.USER_LOCK_FAILED);
        }
    }


    /**
     * Delete user
     */
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting user with ID: {}", id);

        try {
            Account user = findById(id);
            accountRepository.delete(user);
            
            log.info("User deleted successfully: {}", user.getUsername());
        } catch (Exception e) {
            log.error("Failed to delete user with ID: {}", id, e);
            throw new AppException(ErrorCode.USER_DELETE_FAILED);
        }
    }

    /**
     * Save user
     */
    public Account save(Account user) {
        return accountRepository.save(user);
    }

    /**
     * Check if username exists
     */
    public boolean existsByUsername(String username) {
        return accountRepository.existsByUsername(username);
    }

    /**
     * Check if email exists
     */
    public boolean existsByEmail(String email) {
        return accountRepository.existsByEmail(email);
    }

    /**
     * Convert Account entity to AccountResponseDto
     */
    public AccountResponseDto toUserResponse(Account user) {
        AccountResponseDto dto = new AccountResponseDto();
        dto.setId(user.getId());
        dto.setUserId(user.getId()); // accountId
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setEnabled(user.getEnabled());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRoles(userRolePermissionService.getUserRoles(user.getId()).stream()
                .map(Role::getCode)
                .collect(Collectors.toSet()));
        dto.setPermissions(userRolePermissionService.getAllUserPermissions(user.getId()).stream()
        .map(Permission::getCode)
                .collect(Collectors.toSet()));
        return dto;
    }

}


