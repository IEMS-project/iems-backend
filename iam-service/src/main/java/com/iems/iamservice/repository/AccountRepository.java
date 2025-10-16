package com.iems.iamservice.repository;

import com.iems.iamservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    
    Optional<Account> findByUsername(String username);
    Optional<Account> findByEmail(String email);
    Optional<Account> findByUserId(UUID userId);
    
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUserId(UUID userId);
    
    /**
     * Find user by username or email
     */
    @Query("SELECT u FROM Account u WHERE u.username = :usernameOrEmail OR u.email = :usernameOrEmail")
    Optional<Account> findByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);
}


