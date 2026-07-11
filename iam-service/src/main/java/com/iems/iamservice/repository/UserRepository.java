package com.iems.iamservice.repository;

import com.iems.iamservice.dto.response.UserBasicInfoDto;
import com.iems.iamservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByAccountId(UUID accountId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByAccountId(UUID accountId);

    List<User> findByAccountIdIn(Set<UUID> accountIds);

    @Query("""
            SELECT new com.iems.iamservice.dto.response.UserBasicInfoDto(
                u.accountId,
                TRIM(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))),
                u.email,
                u.image
            )
            FROM User u
            JOIN Account a ON u.accountId = a.id
            WHERE a.enabled = true
            AND (
                :keyword = '' OR
                LOWER(TRIM(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, '')))) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            ORDER BY LOWER(COALESCE(u.firstName, '')), LOWER(COALESCE(u.lastName, '')), LOWER(COALESCE(u.email, ''))
            """)
    Page<UserBasicInfoDto> searchBasicInfos(
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT new com.iems.iamservice.dto.response.UserBasicInfoDto(
                u.accountId,
                TRIM(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))),
                u.email,
                u.image
            )
            FROM User u
            JOIN Account a ON u.accountId = a.id
            WHERE a.enabled = true
            AND (
                :keyword = '' OR
                LOWER(TRIM(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, '')))) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            AND u.accountId NOT IN :excludeAccountIds
            ORDER BY LOWER(COALESCE(u.firstName, '')), LOWER(COALESCE(u.lastName, '')), LOWER(COALESCE(u.email, ''))
            """)
    Page<UserBasicInfoDto> searchBasicInfosExcluding(
            @Param("keyword") String keyword,
            @Param("excludeAccountIds") List<UUID> excludeAccountIds,
            Pageable pageable);
}
