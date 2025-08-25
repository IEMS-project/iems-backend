package com.iems.departmentservice.service;

import com.iems.departmentservice.entity.Department;
import com.iems.departmentservice.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DepartmentService {
    @Autowired
    private DepartmentRepository repository;

    public Department saveDepartment(Department department) {
        return repository.save(department);
    }

    public List<Department> getAllDepartments() {
        return repository.findAll();
    }

    public Optional<Department> getDepartmentById(Long id) {
        return repository.findById(id);
    }
}