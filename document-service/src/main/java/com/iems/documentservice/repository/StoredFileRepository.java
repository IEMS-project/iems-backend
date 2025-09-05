package com.iems.documentservice.repository;

import com.iems.documentservice.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    List<StoredFile> findByFolderId(Long folderId);
    List<StoredFile> findByOwnerId(Long ownerId);
    List<StoredFile> findByPermission(StoredFile.Permission permission);
    List<StoredFile> findByIdIn(Collection<Long> ids);
}


