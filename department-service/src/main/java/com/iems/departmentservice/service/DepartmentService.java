package com.iems.departmentservice.service;

import com.iems.departmentservice.client.UserServiceClient;
import com.iems.departmentservice.dto.request.AddUserToDepartmentDto;
import com.iems.departmentservice.dto.request.CreateDepartmentDto;
import com.iems.departmentservice.dto.response.DepartmentResponseDto;
import com.iems.departmentservice.dto.response.DepartmentUserDto;
import com.iems.departmentservice.dto.request.UpdateDepartmentDto;
import com.iems.departmentservice.entity.Department;
import com.iems.departmentservice.entity.DepartmentUser;
import com.iems.departmentservice.repository.DepartmentRepository;
import com.iems.departmentservice.repository.DepartmentUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final DepartmentUserRepository departmentUserRepository;
    private final UserServiceClient userServiceClient;

    public DepartmentResponseDto saveDepartment(CreateDepartmentDto createDto, UUID userId) {
        if (departmentRepository.existsByDepartmentName(createDto.getDepartmentName())) {
            throw new IllegalArgumentException("Department name already exists");
        }
        
        // Validate manager exists and is active
        if (createDto.getManagerId() != null && !userServiceClient.isUserActive(createDto.getManagerId())) {
            throw new IllegalArgumentException("Manager not found or inactive");
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
                    throw new IllegalArgumentException("Department name already exists");
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
            throw new IllegalArgumentException("Department not found");
        }
        
        if (departmentUserRepository.existsByDepartmentIdAndUserIdAndIsActiveTrue(departmentId, addUserDto.getUserId())) {
            throw new IllegalArgumentException("User is already in this department");
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
}