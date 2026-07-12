package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.CreateAccountDto;
import com.iems.iamservice.dto.request.UpdateAccountDto;
import com.iems.iamservice.dto.response.AdminAccountResponseDto;
import com.iems.iamservice.dto.response.AccountResponseDto;
import com.iems.iamservice.entity.Account;
import com.iems.iamservice.entity.User;
import com.iems.iamservice.entity.enums.SubscriptionType;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
import com.iems.iamservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final com.iems.iamservice.repository.UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountStatusCacheService accountStatusCacheService;

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
        } else {
            // Assign default "USER" role if no roles specified
            log.info("No roles specified, assigning default USER role to user {}", savedUser.getId());
            try {
                userRolePermissionService.assignRolesToUser(savedUser.getId(), Set.of("USER"));
            } catch (Exception e) {
                log.warn("Failed to assign default USER role: {}", e.getMessage());
            }
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
     * Searches account information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param keyword the keyword parameter
     * @param pageable the pageable parameter
     * @return the paginated result set
     */
    public Page<AdminAccountResponseDto> searchAdminAccounts(String keyword, Pageable pageable) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Page<Account> page = accountRepository.searchAdminAccounts(normalizedKeyword, pageable);
        Set<UUID> accountIds = page.getContent().stream()
                .map(Account::getId)
                .collect(Collectors.toSet());
        Map<UUID, User> usersByAccountId = userRepository.findByAccountIdIn(accountIds).stream()
                .collect(Collectors.toMap(User::getAccountId, user -> user));

        return page.map(account -> toAdminAccountResponse(account, usersByAccountId.get(account.getId())));
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
     * Finds account information that matches the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param email the email parameter
     * @return an optional result when matching data is available
     */
    public Optional<Account> findByEmail(String email) {
        return accountRepository.findByEmail(email);
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
        accountStatusCacheService.update(updatedUser.getId(), Boolean.TRUE.equals(updatedUser.getEnabled()));
        log.info("User updated successfully: {}", updatedUser.getUsername());
        return updatedUser;
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
            accountStatusCacheService.update(updatedUser.getId(), Boolean.TRUE.equals(updatedUser.getEnabled()));
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
            accountStatusCacheService.evict(id);
            
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
     * Normalizes account content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param account the account parameter
     * @return the normalize expired subscription result
     */
    @Transactional
    public Account normalizeExpiredSubscription(Account account) {
        if (account != null
                && account.getSubscriptionType() == SubscriptionType.PREMIUM
                && account.getPremiumUntil() != null
                && !account.getPremiumUntil().isAfter(Instant.now())) {
            account.setSubscriptionType(SubscriptionType.FREE);
            account.setPremiumUntil(null);
            return accountRepository.save(account);
        }
        return account;
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
     * Upgrade account to Premium
     * @param id account ID
     * @param durationDays number of days of premium (e.g. 30 for monthly)
     */
    @Transactional
    public Account upgradeToPremium(UUID id, int durationDays) {
        Account account = findById(id);
        Instant now = Instant.now();
        // If still premium, extend from current premiumUntil; else start from now
        Instant base = (account.getPremiumUntil() != null && account.getPremiumUntil().isAfter(now))
                ? account.getPremiumUntil() : now;
        account.setSubscriptionType(SubscriptionType.PREMIUM);
        account.setPremiumUntil(base.plusSeconds((long) durationDays * 24 * 60 * 60));
        Account saved = accountRepository.save(account);
        log.info("Account {} upgraded to PREMIUM until {}", id, saved.getPremiumUntil());
        return saved;
    }

    /**
     * Downgrade account to Free
     */
    @Transactional
    public Account downgradeToFree(UUID id) {
        Account account = findById(id);
        account.setSubscriptionType(SubscriptionType.FREE);
        account.setPremiumUntil(null);
        Account saved = accountRepository.save(account);
        log.info("Account {} downgraded to FREE", id);
        return saved;
    }

    /**
     * Convert Account entity to AccountResponseDto
     */
    public AccountResponseDto toUserResponse(Account user) {
        user = normalizeExpiredSubscription(user);
        AccountResponseDto dto = new AccountResponseDto();
        dto.setId(user.getId());
        dto.setUserId(user.getId()); // accountId
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setEnabled(user.getEnabled());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRoles(userRolePermissionService.getUserRoles(user.getId()));
        dto.setSubscriptionType(user.getSubscriptionType());
        dto.setPremiumUntil(user.getPremiumUntil());
        // dto.setPermissions(userRolePermissionService.getAllUserPermissions(user.getId()).stream()
        // .map(Permission::getCode)
        //         .collect(Collectors.toSet()));
        return dto;
    }

    /**
     * Returns to admin account response for account processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param account the account parameter
     * @param user the user parameter
     * @return the to admin account response result
     */
    public AdminAccountResponseDto toAdminAccountResponse(Account account, User user) {
        account = normalizeExpiredSubscription(account);
        String displayName = user == null
                ? null
                : (String.join(" ",
                        Optional.ofNullable(user.getFirstName()).orElse(""),
                        Optional.ofNullable(user.getLastName()).orElse(""))
                .trim());

        return AdminAccountResponseDto.builder()
                .id(account.getId())
                .userId(account.getId())
                .username(account.getUsername())
                .email(account.getEmail())
                .enabled(account.getEnabled())
                .createdAt(account.getCreatedAt())
                .roles(userRolePermissionService.getUserRoles(account.getId()))
                .subscriptionType(account.getSubscriptionType())
                .premiumUntil(account.getPremiumUntil())
                .profileId(user != null ? user.getId() : null)
                .firstName(user != null ? user.getFirstName() : null)
                .lastName(user != null ? user.getLastName() : null)
                .displayName(displayName != null && !displayName.isBlank() ? displayName : account.getUsername())
                .address(user != null ? user.getAddress() : null)
                .phone(user != null ? user.getPhone() : null)
                .dob(user != null ? user.getDob() : null)
                .gender(user != null ? user.getGender() : null)
                .image(user != null ? user.getImage() : null)
                .build();
    }

}
