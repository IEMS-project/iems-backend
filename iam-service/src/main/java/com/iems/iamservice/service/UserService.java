package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.CreateUserDto;
import com.iems.iamservice.dto.request.UpdateUserDto;
import com.iems.iamservice.dto.request.UserIdsDto;
import com.iems.iamservice.dto.response.AccountSubscriptionResponseDto;
import com.iems.iamservice.dto.response.UserBasicInfoDto;
import com.iems.iamservice.dto.response.UserResponseDto;
import com.iems.iamservice.entity.User;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
import com.iems.iamservice.repository.UserRepository;
import com.iems.iamservice.repository.AccountRepository;
import com.iems.iamservice.entity.Account;
import com.iems.iamservice.entity.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {
    @Autowired
    private UserRepository repository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountService accountService;

    /**
     * Create user with account
     * 
     * @deprecated Use AuthService.register() instead for new user registration
     *             This method creates Account first, then User profile
     */
    @Deprecated
    public UserResponseDto createUser(CreateUserDto userRequest) {
        try {
            // In new architecture: Create Account first, then User profile
            if (userRequest.getUsername() == null || userRequest.getPassword() == null) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }

            // Create Account first
            com.iems.iamservice.dto.request.CreateAccountDto accountRequest = new com.iems.iamservice.dto.request.CreateAccountDto();
            accountRequest.setUsername(userRequest.getUsername());
            accountRequest.setEmail(userRequest.getEmail());
            accountRequest.setPassword(userRequest.getPassword());
            accountRequest.setRoleCodes(userRequest.getRoleCodes());

            var createdAccount = accountService.createUser(accountRequest);

            // Create User profile with accountId
            User user = convertToUser(userRequest);
            user.setAccountId(createdAccount.getId());
            User savedUser = repository.save(user);

            return convertToUserResponse(savedUser);
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Updates user data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param id the id parameter
     * @param userRequest the user request parameter
     * @return an optional result when matching data is available
     * @throws AppException if a business rule prevents the requested operation
     */
    public Optional<UserResponseDto> updateUser(UUID id, UpdateUserDto userRequest) {
        try {
            return repository.findById(id)
                    .map(existing -> {
                        applyUpdates(existing, userRequest);
                        return convertToUserResponse(repository.save(existing));
                    })
                    .or(() -> {
                        throw new AppException(ErrorCode.USER_NOT_EXIST);
                    });
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Updates user data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param accountId the account id parameter
     * @param userRequest the user request parameter
     * @return an optional result when matching data is available
     * @throws AppException if a business rule prevents the requested operation
     */
    public Optional<UserResponseDto> updateMyProfile(UUID accountId, CreateUserDto userRequest) {
        try {
            return repository.findByAccountId(accountId)
                    .map(existing -> {
                        if (!hasSelfProfileChanges(existing, userRequest)) {
                            throw new AppException(ErrorCode.NO_CHANGES_DETECTED);
                        }
                        applySelfProfileUpdates(existing, userRequest);
                        return convertToUserResponse(repository.save(existing));
                    })
                    .or(() -> {
                        throw new AppException(ErrorCode.USER_NOT_EXIST);
                    });
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves user information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @return the matching result collection
     * @throws AppException if a business rule prevents the requested operation
     */
    public List<UserBasicInfoDto> getAllUserBasicInfos() {
        try {
            return repository.findAll()
                    .stream()
                    .map(user -> new UserBasicInfoDto(
                            user.getAccountId(),
                            user.getFirstName() + " " + user.getLastName(),
                            user.getEmail(),
                            user.getImage()))
                    .toList();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Searches user information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param query the query parameter
     * @param page the page parameter
     * @param size the size parameter
     * @param excludeAccountIds the exclude account ids parameter
     * @return the paginated result set
     * @throws AppException if a business rule prevents the requested operation
     */
    public Page<UserBasicInfoDto> searchUserBasicInfos(String query, int page, int size, List<UUID> excludeAccountIds) {
        try {
            String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
            int normalizedPage = Math.max(page, 0);
            int normalizedSize = Math.min(Math.max(size, 1), 50);

            Pageable pageable = PageRequest.of(normalizedPage, normalizedSize);

            if (excludeAccountIds == null || excludeAccountIds.isEmpty()) {
                return repository.searchBasicInfos(normalizedQuery, pageable);
            }

            return repository.searchBasicInfosExcluding(normalizedQuery, excludeAccountIds, pageable);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves user information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @return the matching result collection
     * @throws AppException if a business rule prevents the requested operation
     */
    public List<UserBasicInfoDto> getProjectManagerCandidates() {
        try {
            // Only ADMIN role can be project managers
            Set<UUID> accountIds = accountRepository.findAll()
                    .stream()
                    .filter(account -> account.getRole() == UserRole.ADMIN)
                    .map(Account::getId)
                    .collect(Collectors.toSet());

            if (accountIds.isEmpty()) {
                return List.of();
            }

            // Get users by accountIds
            return repository.findAll()
                    .stream()
                    .filter(user -> accountIds.contains(user.getAccountId()))
                    .map(user -> new UserBasicInfoDto(
                            user.getAccountId(),
                            user.getFirstName() + " " + user.getLastName(),
                            user.getEmail(),
                            user.getImage()))
                    .toList();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves user information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param id the id parameter
     * @return an optional result when matching data is available
     * @throws AppException if a business rule prevents the requested operation
     */
    public Optional<UserResponseDto> getUserById(UUID id) {
        try {
            return repository.findById(id)
                    .map(u -> {
                        UserResponseDto dto = convertToUserResponse(u);
                        if (dto != null) {
                            accountRepository.findById(u.getAccountId()).ifPresent(a -> dto.setEnabled(a.getEnabled()));
                        }
                        return dto;
                    })
                    .or(() -> {
                        throw new AppException(ErrorCode.USER_NOT_EXIST);
                    });
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves user information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param accountId the account id parameter
     * @return an optional result when matching data is available
     * @throws AppException if a business rule prevents the requested operation
     */
    public Optional<UserResponseDto> getUserByAccountId(UUID accountId) {
        try {
            return repository.findByAccountId(accountId)
                    .map(u -> {
                        UserResponseDto dto = convertToUserResponse(u);
                        if (dto != null) {
                            accountRepository.findById(accountId).ifPresent(a -> dto.setEnabled(a.getEnabled()));
                        }
                        return dto;
                    })
                    .or(() -> {
                        throw new AppException(ErrorCode.USER_NOT_EXIST);
                    });
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves user information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param accountId the account id parameter
     * @return the get account subscription result
     */
    public AccountSubscriptionResponseDto getAccountSubscription(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXIST));
        account = accountService.normalizeExpiredSubscription(account);
        return new AccountSubscriptionResponseDto(
                account.getId(),
                account.getSubscriptionType(),
                account.getPremiumUntil());
    }

    /**
     * Retrieves user information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param request the request parameter
     * @return the matching result collection
     * @throws AppException if a business rule prevents the requested operation
     */
    public List<UserResponseDto> getUsersByID(UserIdsDto request) {
        try {
            if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
                return List.of();
            }
            List<User> users = repository.findAllById(request.getIds());
            Set<UUID> accountIds = users.stream().map(User::getAccountId).collect(Collectors.toSet());
            List<Account> accounts = accountRepository.findAllById(accountIds);
            Map<UUID, Boolean> enabledMap = accounts.stream()
                    .collect(Collectors.toMap(Account::getId, a -> a.getEnabled() != null ? a.getEnabled() : true, (a1, a2) -> a1));
            return users.stream()
                    .map(u -> {
                        UserResponseDto dto = convertToUserResponse(u);
                        if (dto != null) {
                            dto.setEnabled(enabledMap.getOrDefault(u.getAccountId(), true));
                        }
                        return dto;
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves user information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param request the request parameter
     * @return the matching result collection
     * @throws AppException if a business rule prevents the requested operation
     */
    public List<UserResponseDto> getUsersByAccountIds(com.iems.iamservice.dto.request.AccountIdsDto request) {
        try {
            if (request == null || request.getAccountIds() == null || request.getAccountIds().isEmpty()) {
                return List.of();
            }
            List<User> users = repository.findByAccountIdIn(request.getAccountIds());
            List<Account> accounts = accountRepository.findAllById(request.getAccountIds());
            Map<UUID, Boolean> enabledMap = accounts.stream()
                    .collect(Collectors.toMap(Account::getId, a -> a.getEnabled() != null ? a.getEnabled() : true, (a1, a2) -> a1));
            return users.stream()
                    .map(u -> {
                        UserResponseDto dto = convertToUserResponse(u);
                        if (dto != null) {
                            dto.setEnabled(enabledMap.getOrDefault(u.getAccountId(), true));
                        }
                        return dto;
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Converts user data to the target representation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param userRequest the user request parameter
     * @return the convert to user result
     */
    public User convertToUser(CreateUserDto userRequest) {
        if (userRequest == null)
            return null;
        User user = new User();
        user.setFirstName(userRequest.getFirstName());
        user.setLastName(userRequest.getLastName());
        user.setEmail(userRequest.getEmail());
        user.setAddress(userRequest.getAddress());
        user.setPhone(userRequest.getPhone());
        user.setDob(userRequest.getDob());
        user.setGender(userRequest.getGender());
        user.setImage(userRequest.getImage());
        return user;
    }

    /**
     * Converts user data to the target representation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param user the user parameter
     * @return the convert to user response result
     */
    public UserResponseDto convertToUserResponse(User user) {
        if (user == null)
            return null;
        return new UserResponseDto(
                user.getAccountId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAddress(),
                user.getPhone(),
                user.getDob(),
                user.getGender(),
                user.getImage());
    }

    /**
     * Applies user changes.
     *
     * @param user the user parameter
     * @param userRequest the user request parameter
     */
    private void applyUpdates(User user, UpdateUserDto userRequest) {
        if (userRequest.getFirstName() != null)
            user.setFirstName(userRequest.getFirstName());
        if (userRequest.getLastName() != null)
            user.setLastName(userRequest.getLastName());
        if (userRequest.getEmail() != null)
            user.setEmail(userRequest.getEmail());
        if (userRequest.getAddress() != null)
            user.setAddress(userRequest.getAddress());
        if (userRequest.getPhone() != null)
            user.setPhone(userRequest.getPhone());
        if (userRequest.getDob() != null)
            user.setDob(userRequest.getDob());
        if (userRequest.getGender() != null)
            user.setGender(userRequest.getGender());
        if (userRequest.getImage() != null)
            user.setImage(userRequest.getImage());

    }

    /**
     * Applies user changes.
     *
     * @param user the user parameter
     * @param userRequest the user request parameter
     */
    private void applySelfProfileUpdates(User user, CreateUserDto userRequest) {
        if (userRequest.getFirstName() != null)
            user.setFirstName(userRequest.getFirstName());
        if (userRequest.getLastName() != null)
            user.setLastName(userRequest.getLastName());
        if (userRequest.getEmail() != null)
            user.setEmail(userRequest.getEmail());
        if (userRequest.getAddress() != null)
            user.setAddress(userRequest.getAddress());
        if (userRequest.getPhone() != null)
            user.setPhone(userRequest.getPhone());
        if (userRequest.getDob() != null)
            user.setDob(userRequest.getDob());
        if (userRequest.getGender() != null)
            user.setGender(userRequest.getGender());
        if (userRequest.getImage() != null)
            user.setImage(userRequest.getImage());
    }

    /**
     * Returns has self profile changes for user processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param user the user parameter
     * @param userRequest the user request parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean hasSelfProfileChanges(User user, CreateUserDto userRequest) {
        return (userRequest.getFirstName() != null && !Objects.equals(user.getFirstName(), userRequest.getFirstName()))
                || (userRequest.getLastName() != null && !Objects.equals(user.getLastName(), userRequest.getLastName()))
                || (userRequest.getEmail() != null && !Objects.equals(user.getEmail(), userRequest.getEmail()))
                || (userRequest.getAddress() != null && !Objects.equals(user.getAddress(), userRequest.getAddress()))
                || (userRequest.getPhone() != null && !Objects.equals(user.getPhone(), userRequest.getPhone()))
                || (userRequest.getDob() != null && !Objects.equals(user.getDob(), userRequest.getDob()))
                || (userRequest.getGender() != null && !Objects.equals(user.getGender(), userRequest.getGender()))
                || (userRequest.getImage() != null && !Objects.equals(user.getImage(), userRequest.getImage()));
    }

    /**
     * Updates user data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param accountId the account id parameter
     * @param imageUrl the image url parameter
     * @return an optional result when matching data is available
     * @throws AppException if a business rule prevents the requested operation
     */
    public Optional<UserResponseDto> updateAvatarByAccountId(UUID accountId, String imageUrl) {
        try {
            return repository.findByAccountId(accountId)
                    .map(existing -> {
                        existing.setImage(imageUrl);
                        return convertToUserResponse(repository.save(existing));
                    })
                    .or(() -> {
                        throw new AppException(ErrorCode.USER_NOT_EXIST);
                    });
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves user information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param accountId the account id parameter
     * @return the get notification preferences result
     */
    public String getNotificationPreferences(UUID accountId) {
        return repository.findByAccountId(accountId)
                .map(User::getNotificationPreferences)
                .orElse(null);
    }

    /**
     * Updates user data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param accountId the account id parameter
     * @param preferencesJson the preferences json parameter
     */
    public void updateNotificationPreferences(UUID accountId, String preferencesJson) {
        repository.findByAccountId(accountId).ifPresent(user -> {
            user.setNotificationPreferences(preferencesJson);
            repository.save(user);
        });
    }
}
