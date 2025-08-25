package com.iems.departmentservice.service;

import com.iems.departmentservice.dto.CreateDepartmentDto;
import com.iems.departmentservice.dto.DepartmentResponseDto;
import com.iems.departmentservice.dto.UpdateDepartmentDto;
import com.iems.departmentservice.entity.Department;
import com.iems.departmentservice.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DepartmentService {
    @Autowired
    private DepartmentRepository repository;

    public DepartmentResponseDto saveDepartment(CreateDepartmentDto createDto, UUID userId) {
        if (repository.existsByDepartmentName(createDto.getDepartmentName())) {
            throw new IllegalArgumentException("Department name already exists");
        }
        Department department = new Department();
        department.setDepartmentName(createDto.getDepartmentName());
        department.setDescription(createDto.getDescription());
        department.setManagerId(createDto.getManagerId());
        department.setCreatedBy(userId);
        department.setUpdatedBy(userId);
        Department savedDept = repository.save(department);
        return convertToResponseDto(savedDept);
    }

    public List<DepartmentResponseDto> getAllDepartments() {
        return repository.findAll().stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    public Optional<DepartmentResponseDto> getDepartmentById(UUID id) {
        return repository.findById(id).map(this::convertToResponseDto);
    }

    public Optional<DepartmentResponseDto> updateDepartment(UUID id, UpdateDepartmentDto updateDto, UUID userId) {
        return repository.findById(id).map(existing -> {
            if (updateDto.getDepartmentName() != null && !updateDto.getDepartmentName().isBlank()) {
                if (repository.existsByDepartmentNameAndIdNot(updateDto.getDepartmentName(), id)) {
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
            Department saved = repository.save(existing);
            return convertToResponseDto(saved);
        });
    }

    public boolean deleteDepartment(UUID id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }

    private DepartmentResponseDto convertToResponseDto(Department department) {
        return new DepartmentResponseDto(
                department.getId(),
                department.getDepartmentName(),
                department.getDescription(),
                department.getManagerId()
        );
    }
}