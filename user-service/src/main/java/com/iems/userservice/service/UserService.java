package com.iems.userservice.service;

import com.iems.userservice.client.DepartmentServiceFeignClient;
import com.iems.userservice.client.IamServiceFeignClient;
import com.iems.userservice.dto.request.AddUserToDepartmentDto;
import com.iems.userservice.dto.request.CreateAccountRequestDto;
import com.iems.userservice.dto.request.CreateUserDto;
import com.iems.userservice.dto.request.UpdateUserDto;
import com.iems.userservice.dto.response.UserResponseDto;
import com.iems.userservice.exception.AppException;
import com.iems.userservice.exception.UserErrorCode;
import com.iems.userservice.entity.User;
import com.iems.userservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    @Autowired
    private UserRepository repository;
    
    @Autowired
    private IamServiceFeignClient iamServiceFeignClient;
    @Autowired
    private DepartmentServiceFeignClient departmentServiceFeignClient;

    public UserResponseDto createUser(CreateUserDto userRequest) {
        try {
            // Lưu user vào database trước
            User savedUser = repository.save(convertToUser(userRequest));
            
            // Nếu có thông tin tạo account, gọi IAM service
            if (userRequest.getUsername() != null && userRequest.getPassword() != null) {
                try {
                    CreateAccountRequestDto accountRequest = new CreateAccountRequestDto();
                    accountRequest.setUserId(savedUser.getId());
                    accountRequest.setUsername(userRequest.getUsername());
                    accountRequest.setEmail(userRequest.getEmail());
                    accountRequest.setPassword(userRequest.getPassword());
                    accountRequest.setRoleCodes(userRequest.getRoleCodes());
                    
                    iamServiceFeignClient.createAccount(accountRequest);
                } catch (Exception e) {
                    // Log lỗi nhưng không rollback user đã tạo
                    System.err.println("Failed to create account for user: " + e.getMessage());
                }
            }
            if (userRequest.getDepartmentId() != null) {
                try {
                    System.out.println("🔄 Adding user " + savedUser.getId() + " to department " + userRequest.getDepartmentId());
                    AddUserToDepartmentDto addUserDto = new AddUserToDepartmentDto();
                    addUserDto.setUserId(savedUser.getId());
                    
                    if (departmentServiceFeignClient == null) {
                        System.err.println("❌ DepartmentServiceFeignClient is NULL!");
                        throw new RuntimeException("DepartmentServiceFeignClient is not properly initialized");
                    }
                    
                    var response = departmentServiceFeignClient.addUserToDepartment(userRequest.getDepartmentId(), addUserDto);
                    System.out.println("✅ Successfully added user to department. Response: " + response);
                } catch (Exception e) {
                    // Log lỗi nhưng không rollback user đã tạo
                    System.err.println("⚠️ Failed to add user to department: " + e.getMessage());
                    e.printStackTrace();
                }
            }


            return convertToUserResponse(savedUser);
        } catch (Exception ex) {
            throw new AppException(UserErrorCode.INTERNAL_SERVER_ERROR);
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
                        throw new AppException(UserErrorCode.USER_NOT_FOUND);
                    });
        } catch (AppException e) {
            throw e; // giữ nguyên exception custom
        } catch (Exception ex) {
            throw new AppException(UserErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public Optional<UserResponseDto> updateMyProfile(UUID id, CreateUserDto userRequest) {
        try {
            return repository.findById(id)
                    .map(existing -> {
                        applySelfProfileUpdates(existing, userRequest);
                        return convertToUserResponse(repository.save(existing));
                    })
                    .or(() -> {
                        throw new AppException(UserErrorCode.USER_NOT_FOUND);
                    });
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(UserErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public List<UserResponseDto> getAllUsers() {
        try {
            return repository.findAll()
                    .stream()
                    .map(this::convertToUserResponse)
                    .toList();
        } catch (Exception ex) {
            throw new AppException(UserErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public Optional<UserResponseDto> getUserById(UUID id) {
        try {
            return repository.findById(id)
                    .map(this::convertToUserResponse)
                    .or(() -> {
                        throw new AppException(UserErrorCode.USER_NOT_FOUND);
                    });
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(UserErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteUser(UUID id) {
        try {
            if (!repository.existsById(id)) {
                throw new AppException(UserErrorCode.USER_NOT_FOUND);
            }
            repository.deleteById(id);
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(UserErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // ----- convert & apply methods giữ nguyên -----
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
        user.setPersonalID(userRequest.getPersonalID());
        user.setImage(userRequest.getImage());
        user.setBankAccountNumber(userRequest.getBankAccountNumber());
        user.setBankName(userRequest.getBankName());
        user.setContractType(userRequest.getContractType());
        user.setStartDate(userRequest.getStartDate());
        user.setRole(userRequest.getRole());
        return user;
    }

    public UserResponseDto convertToUserResponse(User user) {
        if (user == null) return null;
        return new UserResponseDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAddress(),
                user.getPhone(),
                user.getDob(),
                user.getGender(),
                user.getPersonalID(),
                user.getImage(),
                user.getBankAccountNumber(),
                user.getBankName(),
                user.getContractType(),
                user.getStartDate(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
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
        if (userRequest.getPersonalID() != null) user.setPersonalID(userRequest.getPersonalID());
        if (userRequest.getImage() != null) user.setImage(userRequest.getImage());
        if (userRequest.getBankAccountNumber() != null) user.setBankAccountNumber(userRequest.getBankAccountNumber());
        if (userRequest.getBankName() != null) user.setBankName(userRequest.getBankName());
        if (userRequest.getContractType() != null) user.setContractType(userRequest.getContractType());
        if (userRequest.getStartDate() != null) user.setStartDate(userRequest.getStartDate());
        if (userRequest.getRole() != null) user.setRole(userRequest.getRole());
    }

    private void applySelfProfileUpdates(User user, CreateUserDto userRequest) {
        if (userRequest.getAddress() != null) user.setAddress(userRequest.getAddress());
        if (userRequest.getPhone() != null) user.setPhone(userRequest.getPhone());
        if (userRequest.getImage() != null) user.setImage(userRequest.getImage());
        if (userRequest.getBankAccountNumber() != null) user.setBankAccountNumber(userRequest.getBankAccountNumber());
        if (userRequest.getBankName() != null) user.setBankName(userRequest.getBankName());
    }
}
