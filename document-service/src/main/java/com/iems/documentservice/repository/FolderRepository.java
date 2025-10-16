package com.iems.documentservice.repository;

import com.iems.documentservice.entity.Folder;
import com.iems.documentservice.entity.enums.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    List<Folder> findByParentId(UUID parentId);
    List<Folder> findByOwnerId(UUID ownerId);
    List<Folder> findByPermission(Permission permission);
    List<Folder> findByIdIn(List<UUID> ids);
}


