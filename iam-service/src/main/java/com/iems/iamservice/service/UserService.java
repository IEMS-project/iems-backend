package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.CreateUserDto;
import com.iems.iamservice.dto.request.UpdateUserDto;
import com.iems.iamservice.dto.request.UserIdsDto;
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
     * @deprecated Use AuthService.register() instead for new user registration
     * This method creates Account first, then User profile
     */
    @Deprecated
    public UserResponseDto createUser(CreateUserDto userRequest) {
        try {
            // In new architecture: Create Account first, then User profile
            if (userRequest.getUsername() == null || userRequest.getPassword() == null) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            
            // Create Account first
            com.iems.iamservice.dto.request.CreateAccountDto accountRequest = 
                new com.iems.iamservice.dto.request.CreateAccountDto();
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

    public Optional<UserResponseDto> updateMyProfile(UUID accountId, CreateUserDto userRequest) {
        try {
            return repository.findByAccountId(accountId)
                    .map(existing -> {
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

    public List<UserResponseDto> getAllUsers() {
        try {
            return repository.findAll()
                    .stream()
                    .map(this::convertToUserResponse)
                    .toList();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public List<UserBasicInfoDto> getAllUserBasicInfos() {
        try {
            return repository.findAll()
                    .stream()
                    .map(user -> new UserBasicInfoDto(
                            user.getAccountId(),
                            user.getFirstName() + " " + user.getLastName(),
                            user.getEmail(),
                            user.getImage()
                    ))
                    .toList();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

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
                            user.getImage()
                    ))
                    .toList();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public Optional<UserResponseDto> getUserById(UUID id) {
        try {
            return repository.findById(id)
                    .map(this::convertToUserResponse)
                    .or(() -> {
                        throw new AppException(ErrorCode.USER_NOT_EXIST);
                    });
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public Optional<UserResponseDto> getUserByAccountId(UUID accountId) {
        try {
            return repository.findByAccountId(accountId)
                    .map(this::convertToUserResponse)
                    .or(() -> {
                        throw new AppException(ErrorCode.USER_NOT_EXIST);
                    });
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public List<UserResponseDto> getUsersByID(UserIdsDto request) {
        try {
            if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
                return List.of();
            }
            return repository.findAllById(request.getIds())
                    .stream()
                    .map(this::convertToUserResponse)
                    .toList();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public List<UserResponseDto> getUsersByAccountIds(com.iems.iamservice.dto.request.AccountIdsDto request) {
        try {
            if (request == null || request.getAccountIds() == null || request.getAccountIds().isEmpty()) {
                return List.of();
            }
            return repository.findByAccountIdIn(request.getAccountIds())
                    .stream()
                    .map(this::convertToUserResponse)
                    .toList();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteUser(UUID id) {
        try {
            if (!repository.existsById(id)) {
                throw new AppException(ErrorCode.USER_NOT_EXIST);
            }
            repository.deleteById(id);
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public User convertToUser(CreateUserDto userRequest) {
        if (userRequest == null) return null;
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

    public UserResponseDto convertToUserResponse(User user) {
        if (user == null) return null;
        return new UserResponseDto(
                user.getAccountId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAddress(),
                user.getPhone(),
                user.getDob(),
                user.getGender(),
                user.getImage()
        );
    }

    private void applyUpdates(User user, UpdateUserDto userRequest) {
        if (userRequest.getFirstName() != null) user.setFirstName(userRequest.getFirstName());
        if (userRequest.getLastName() != null) user.setLastName(userRequest.getLastName());
        if (userRequest.getEmail() != null) user.setEmail(userRequest.getEmail());
        if (userRequest.getAddress() != null) user.setAddress(userRequest.getAddress());
        if (userRequest.getPhone() != null) user.setPhone(userRequest.getPhone());
        if (userRequest.getDob() != null) user.setDob(userRequest.getDob());
        if (userRequest.getGender() != null) user.setGender(userRequest.getGender());
        if (userRequest.getImage() != null) user.setImage(userRequest.getImage());

    }

    private void applySelfProfileUpdates(User user, CreateUserDto userRequest) {
        if (userRequest.getAddress() != null) user.setAddress(userRequest.getAddress());
        if (userRequest.getPhone() != null) user.setPhone(userRequest.getPhone());
        if (userRequest.getImage() != null) user.setImage(userRequest.getImage());
    }

    public Optional<UserResponseDto> updateAvatar(UUID id, String imageUrl) {
        try {
            return repository.findById(id)
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
}
