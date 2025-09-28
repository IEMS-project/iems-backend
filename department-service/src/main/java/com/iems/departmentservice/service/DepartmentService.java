package com.iems.departmentservice.service;

import com.iems.departmentservice.client.UserServiceFeignClient;
import com.iems.departmentservice.dto.request.AddUserToDepartmentDto;
import com.iems.departmentservice.dto.request.CreateDepartmentDto;
import com.iems.departmentservice.dto.response.*;
import com.iems.departmentservice.dto.request.UpdateDepartmentDto;
import com.iems.departmentservice.entity.Department;
import com.iems.departmentservice.entity.DepartmentUser;
import com.iems.departmentservice.repository.DepartmentRepository;
import com.iems.departmentservice.exception.AppException;
import com.iems.departmentservice.exception.DepartmentErrorCode;
import com.iems.departmentservice.repository.DepartmentUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DepartmentService {
    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DepartmentUserRepository departmentUserRepository;

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    public DepartmentResponseDto saveDepartment(CreateDepartmentDto createDto, UUID userId) {
        if (departmentRepository.existsByDepartmentName(createDto.getDepartmentName())) {
            throw new AppException(DepartmentErrorCode.DEPARTMENT_NAME_EXISTS);
        }
        Department department = new Department();
        department.setDepartmentName(createDto.getDepartmentName());
        department.setDescription(createDto.getDescription());
        department.setManagerId(createDto.getManagerId());
        department.setCreatedBy(userId);
        department.setUpdatedBy(userId);
        Department savedDept = departmentRepository.save(department);
        return convertToResponseDto(savedDept);
    }

    public List<DepartmentResponseDto> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    public Optional<DepartmentResponseDto> getDepartmentById(UUID id) {
        return departmentRepository.findById(id).map(this::convertToResponseDto);
    }

    public Optional<DepartmentResponseDto> updateDepartment(UUID id, UpdateDepartmentDto updateDto, UUID userId) {
        return departmentRepository.findById(id).map(existing -> {
            if (updateDto.getDepartmentName() != null && !updateDto.getDepartmentName().isBlank()) {
                if (departmentRepository.existsByDepartmentNameAndIdNot(updateDto.getDepartmentName(), id)) {
                    throw new AppException(DepartmentErrorCode.DEPARTMENT_NAME_EXISTS);
                }
                existing.setDepartmentName(updateDto.getDepartmentName());
            }
            if (updateDto.getDescription() != null) {
                existing.setDescription(updateDto.getDescription());
            }
            if (updateDto.getManagerId() != null) {
                existing.setManagerId(updateDto.getManagerId());
            }
            existing.setUpdatedBy(userId);
            Department saved = departmentRepository.save(existing);
            return convertToResponseDto(saved);
        });
    }

    public boolean deleteDepartment(UUID id) {
        if (!departmentRepository.existsById(id)) {
            return false;
        }
        departmentRepository.deleteById(id);
        return true;
    }

    public DepartmentUserDto addUserToDepartment(UUID departmentId, AddUserToDepartmentDto addUserDto, UUID currentUserId) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new AppException(DepartmentErrorCode.DEPARTMENT_NOT_FOUND);
        }

        if (departmentUserRepository.existsByDepartmentIdAndUserIdAndIsActiveTrue(departmentId, addUserDto.getUserId())) {
            throw new AppException(DepartmentErrorCode.USER_ALREADY_IN_DEPARTMENT);
        }

        DepartmentUser departmentUser = new DepartmentUser();
        departmentUser.setDepartmentId(departmentId);
        departmentUser.setUserId(addUserDto.getUserId());
        departmentUser.setIsActive(true);
        departmentUser.setCreatedBy(currentUserId);
        departmentUser.setUpdatedBy(currentUserId);

        DepartmentUser saved = departmentUserRepository.save(departmentUser);
        return convertToDepartmentUserDto(saved);
    }

    public boolean removeUserFromDepartment(UUID departmentId, UUID userId, UUID currentUserId) {
        Optional<DepartmentUser> departmentUserOpt = departmentUserRepository
                .findByDepartmentIdAndUserIdAndIsActiveTrue(departmentId, userId);

        if (departmentUserOpt.isPresent()) {
            DepartmentUser departmentUser = departmentUserOpt.get();
            departmentUser.setIsActive(false);
            departmentUser.setLeftAt(LocalDateTime.now());
            departmentUser.setUpdatedBy(currentUserId);
            departmentUserRepository.save(departmentUser);
            return true;
        }
        return false;
    }

    public List<DepartmentUserDto> getDepartmentsOfUser(UUID userId) {
        List<DepartmentUser> departmentUsers = departmentUserRepository.findActiveDepartmentsByUserId(userId);
        return departmentUsers.stream()
                .map(this::convertToDepartmentUserDto)
                .collect(Collectors.toList());
    }

    private DepartmentResponseDto convertToResponseDto(Department department) {
        List<DepartmentUser> allUsers = departmentUserRepository.findByDepartmentId(department.getId());
        List<DepartmentUser> activeUsers = departmentUserRepository.findActiveUsersByDepartmentId(department.getId());

        List<DepartmentUserDto> userDtos = allUsers.stream()
                .map(this::convertToDepartmentUserDto)
                .collect(Collectors.toList());

        return new DepartmentResponseDto(
                department.getId(),
                department.getDepartmentName(),
                department.getDescription(),
                department.getManagerId(),
                userDtos,
                allUsers.size(),
                activeUsers.size()
        );
    }

    private DepartmentUserDto convertToDepartmentUserDto(DepartmentUser departmentUser) {
        return new DepartmentUserDto(
                departmentUser.getId(),
                departmentUser.getDepartmentId(),
                departmentUser.getUserId(),
                departmentUser.getJoinedAt(),
                departmentUser.getLeftAt(),
                departmentUser.getIsActive()
        );
    }

    public Optional<DepartmentWithUsersDto> getDepartmentWithUsersById(UUID id) {
        return departmentRepository.findById(id).map(department -> {
            List<DepartmentUser> allUsers = departmentUserRepository.findByDepartmentId(department.getId());
            List<DepartmentUser> activeUsers = departmentUserRepository.findActiveUsersByDepartmentId(department.getId());

            // Get user IDs
            List<UUID> userIds = allUsers.stream()
                    .map(DepartmentUser::getUserId)
                    .collect(Collectors.toList());

            // Fetch user details from User Service using FeignClient
            List<UserDetailDto> userDetails = getUsersByIds(userIds);

            // Create enriched department users
            List<DepartmentUserWithDetailsDto> enrichedUsers = allUsers.stream()
                    .map(deptUser -> {
                        UserDetailDto userDetail = userDetails.stream()
                                .filter(user -> user.getId().equals(deptUser.getUserId()))
                                .findFirst()
                                .orElse(null);

                        return new DepartmentUserWithDetailsDto(
                                deptUser.getId(),
                                deptUser.getDepartmentId(),
                                deptUser.getUserId(),
                                deptUser.getJoinedAt(),
                                deptUser.getLeftAt(),
                                deptUser.getIsActive(),
                                userDetail
                        );
                    })
                    .collect(Collectors.toList());

            return new DepartmentWithUsersDto(
                    department.getId(),
                    department.getDepartmentName(),
                    department.getDescription(),
                    department.getManagerId(),
                    department.getCreatedAt(),
                    department.getCreatedBy(),
                    department.getUpdatedAt(),
                    department.getUpdatedBy(),
                    enrichedUsers,
                    allUsers.size(),
                    activeUsers.size()
            );
        });
    }

    private List<UserDetailDto> getUsersByIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        return userIds.stream()
                .map(this::getUserById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<UserDetailDto> getUserById(UUID userId) {
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUserById(userId);

            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userData = (Map<String, Object>) response.getBody().get("data");
                return Optional.of(convertToUserDetailDto(userData));
            }

            return Optional.empty();
        } catch (Exception e) {
            // Log error and return empty
            System.err.println("Error fetching user " + userId + " from User Service: " + e.getMessage());
            return Optional.empty();
        }
    }

    private UserDetailDto convertToUserDetailDto(Map<String, Object> userData) {
        UserDetailDto dto = new UserDetailDto();
        dto.setId(UUID.fromString(userData.get("id").toString()));
        dto.setFirstName((String) userData.get("firstName"));
        dto.setLastName((String) userData.get("lastName"));
        dto.setEmail((String) userData.get("email"));
        dto.setAddress((String) userData.get("address"));
        dto.setPhone((String) userData.get("phone"));

        // Handle Date objects - convert to string
        Object dob = userData.get("dob");
        dto.setDob(dob != null ? dob.toString() : null);

        // Handle enum objects - convert to string
        Object gender = userData.get("gender");
        dto.setGender(gender != null ? gender.toString() : null);

        dto.setPersonalID((String) userData.get("personalID"));
        dto.setImage((String) userData.get("image"));
        dto.setBankAccountNumber((String) userData.get("bankAccountNumber"));
        dto.setBankName((String) userData.get("bankName"));

        // Handle enum objects - convert to string
        Object contractType = userData.get("contractType");
        dto.setContractType(contractType != null ? contractType.toString() : null);

        // Handle Date objects - convert to string
        Object startDate = userData.get("startDate");
        dto.setStartDate(startDate != null ? startDate.toString() : null);

        dto.setRole((String) userData.get("role"));
        return dto;
    }
}