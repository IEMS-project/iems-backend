package com.iems.departmentservice.service;

import com.iems.departmentservice.client.UserServiceFeignClient;
// removed unused import
import com.iems.departmentservice.dto.request.AddUsersToDepartmentDto;
import com.iems.departmentservice.dto.request.CreateDepartmentDto;
import com.iems.departmentservice.dto.response.*;
import com.iems.departmentservice.dto.request.UpdateDepartmentDto;
import com.iems.departmentservice.entity.Department;
import com.iems.departmentservice.entity.DepartmentUser;
import com.iems.departmentservice.repository.DepartmentRepository;
import com.iems.departmentservice.exception.AppException;
import com.iems.departmentservice.exception.DepartmentErrorCode;
import com.iems.departmentservice.repository.DepartmentUserRepository;
import com.iems.departmentservice.security.JwtUserDetails;
// removed unused import
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// removed unused import
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

    public DepartmentResponseDto saveDepartment(CreateDepartmentDto createDto) {
        if (departmentRepository.existsByDepartmentName(createDto.getDepartmentName())) {
            throw new AppException(DepartmentErrorCode.DEPARTMENT_NAME_EXISTS);
        }
        UUID userId = this.getUserIdFromRequest();
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

    public Optional<DepartmentResponseDto> updateDepartment(UUID id, UpdateDepartmentDto updateDto) {
        return departmentRepository.findById(id).map(existing -> {
            UUID userId = this.getUserIdFromRequest();
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

    public List<DepartmentUserDto> addUsersToDepartment(UUID departmentId, AddUsersToDepartmentDto addUserDto) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new AppException(DepartmentErrorCode.DEPARTMENT_NOT_FOUND);
        }

        UUID currentUserId = this.getUserIdFromRequest();
        List<DepartmentUser> toSave = addUserDto.getUserIds().stream()
                .filter(userId -> !departmentUserRepository.existsByDepartmentIdAndUserId(departmentId, userId))
                .map(userId -> {
                    DepartmentUser du = new DepartmentUser();
                    du.setDepartmentId(departmentId);
                    du.setUserId(userId);
                    du.setCreatedBy(currentUserId);
                    du.setUpdatedBy(currentUserId);
                    return du;
                })
                .collect(Collectors.toList());

        List<DepartmentUser> saved = departmentUserRepository.saveAll(toSave);
        return saved.stream().map(this::convertToDepartmentUserDto).collect(Collectors.toList());
    }

    public boolean removeUserFromDepartment(UUID departmentId, UUID userId) {
        Optional<DepartmentUser> departmentUserOpt = departmentUserRepository
                .findByDepartmentIdAndUserId(departmentId, userId);
        if (departmentUserOpt.isPresent()) {
            departmentUserRepository.deleteByDepartmentIdAndUserId(departmentId, userId);
            return true;
        }
        return false;
    }

    public List<DepartmentMemberCountDto> getDepartmentsWithMemberCounts() {
        return departmentRepository.findAll().stream()
                .map(dept -> new DepartmentMemberCountDto(
                        dept.getId(),
                        dept.getDepartmentName(),
                        departmentUserRepository.countUsersByDepartmentId(dept.getId())
                ))
                .collect(Collectors.toList());
    }

    public List<DepartmentUserDto> getDepartmentsOfUser(UUID userId) {
        List<DepartmentUser> departmentUsers = departmentUserRepository.findDepartmentsByUserId(userId);
        return departmentUsers.stream()
                .map(this::convertToDepartmentUserDto)
                .collect(Collectors.toList());
    }

    private DepartmentResponseDto convertToResponseDto(Department department) {
        List<DepartmentUser> allUsers = departmentUserRepository.findByDepartmentId(department.getId());

        List<DepartmentUserDto> userDtos = allUsers.stream()
                .map(this::convertToDepartmentUserDto)
                .collect(Collectors.toList());

        return new DepartmentResponseDto(
                department.getId(),
                department.getDepartmentName(),
                department.getDescription(),
                department.getManagerId(),
                userDtos,
                allUsers.size()
        );
    }

    private DepartmentUserDto convertToDepartmentUserDto(DepartmentUser departmentUser) {
        return new DepartmentUserDto(
                departmentUser.getId(),
                departmentUser.getDepartmentId(),
                departmentUser.getUserId()
        );
    }

    public Optional<DepartmentWithUsersDto> getDepartmentWithUsersById(UUID id) {
        return departmentRepository.findById(id).map(department -> {
            List<DepartmentUser> allUsers = departmentUserRepository.findByDepartmentId(department.getId());

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
                    allUsers.size()
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

    public UUID getUserIdFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();
        return userId;
    }
}