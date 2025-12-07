package com.iems.iamservice.repository;

import com.iems.iamservice.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);
    boolean existsByCode(String code);
    List<Role> findByCodeIn(Set<String> codes);

}


