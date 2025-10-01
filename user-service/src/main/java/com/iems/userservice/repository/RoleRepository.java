package com.iems.userservice.repository;

import com.iems.userservice.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    @Query("SELECT CASE WHEN COUNT(r)>0 THEN true ELSE false END FROM Role r WHERE LOWER(r.name) = LOWER(:name)")
	boolean existsByName(String name);

    @Query("SELECT r FROM Role r WHERE LOWER(r.name) = LOWER(:name)")
	Optional<Role> findByName(String name);
}
