package com.iems.documentservice.repository;

import com.iems.documentservice.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.iems.documentservice.entity.enums.Permission;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    List<StoredFile> findByFolderId(UUID folderId);
    List<StoredFile> findByOwnerId(UUID ownerId);
    List<StoredFile> findByPermission(Permission permission);
    List<StoredFile> findByIdIn(Collection<UUID> ids);
    java.util.Optional<StoredFile> findFirstByOwnerIdAndPathStartingWithOrderByCreatedAtDesc(UUID ownerId, String pathPrefix);
    java.util.Optional<StoredFile> findFirstByPathStartingWithOrderByCreatedAtDesc(String pathPrefix);
}


