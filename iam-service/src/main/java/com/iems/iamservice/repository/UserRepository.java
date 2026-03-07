package com.iems.iamservice.repository;

import com.iems.iamservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByAccountId(UUID accountId);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByAccountId(UUID accountId);
}
