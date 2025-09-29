package com.iems.departmentservice.repository;

import com.iems.departmentservice.entity.DepartmentUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentUserRepository extends JpaRepository<DepartmentUser, UUID> {
    
    @Query("SELECT du FROM DepartmentUser du WHERE du.departmentId = :departmentId")
    List<DepartmentUser> findUsersByDepartmentId(@Param("departmentId") UUID departmentId);
    
    @Query("SELECT du FROM DepartmentUser du WHERE du.userId = :userId")
    List<DepartmentUser> findDepartmentsByUserId(@Param("userId") UUID userId);
    
    boolean existsByDepartmentIdAndUserId(UUID departmentId, UUID userId);
    
    Optional<DepartmentUser> findByDepartmentIdAndUserId(UUID departmentId, UUID userId);
    
    @Query("SELECT COUNT(du) FROM DepartmentUser du WHERE du.departmentId = :departmentId")
    long countUsersByDepartmentId(@Param("departmentId") UUID departmentId);
    
    List<DepartmentUser> findByDepartmentId(UUID departmentId);
    
    List<DepartmentUser> findByUserId(UUID userId);

    void deleteByDepartmentIdAndUserId(UUID departmentId, UUID userId);
}
