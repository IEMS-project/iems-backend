package com.iems.iamservice.repository;

import com.iems.iamservice.entity.Account;
import com.iems.iamservice.entity.enums.SubscriptionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    
    Optional<Account> findByUsername(String username);
    Optional<Account> findByEmail(String email);
    
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    
    /**
     * Find user by username or email
     */
    @Query("SELECT u FROM Account u WHERE u.username = :usernameOrEmail OR u.email = :usernameOrEmail")
    Optional<Account> findByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);

    @Query("""
            SELECT a
            FROM Account a
            LEFT JOIN User u ON u.accountId = a.id
            WHERE (
                :keyword = '' OR
                LOWER(COALESCE(a.username, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(COALESCE(a.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(TRIM(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, '')))) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            """)
    Page<Account> searchAdminAccounts(@Param("keyword") String keyword, Pageable pageable);

    /** Find all accounts with a given subscription type (used by scheduler). */
    List<Account> findBySubscriptionType(SubscriptionType subscriptionType);
}


