package com.iems.projectservice.repository;

import com.iems.projectservice.entity.MemberPermission;
import com.iems.projectservice.entity.enums.ProjectPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberPermissionRepository extends JpaRepository<MemberPermission, UUID> {

    List<MemberPermission> findByProjectIdAndAccountId(UUID projectId, UUID accountId);

    Optional<MemberPermission> findByProjectIdAndAccountIdAndPermission(
            UUID projectId, UUID accountId, ProjectPermission permission);

    void deleteByProjectIdAndAccountIdAndPermission(
            UUID projectId, UUID accountId, ProjectPermission permission);

    void deleteByProjectIdAndAccountId(UUID projectId, UUID accountId);

        void deleteByProjectId(UUID projectId);
}
