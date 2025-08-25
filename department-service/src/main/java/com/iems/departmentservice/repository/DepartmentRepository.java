package com.iems.departmentservice.repository;

import com.iems.departmentservice.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    boolean existsByDepartmentName(String departmentName);
    boolean existsByDepartmentNameAndIdNot(String departmentName, UUID id);
}