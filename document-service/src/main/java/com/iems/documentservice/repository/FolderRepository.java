package com.iems.documentservice.repository;

import com.iems.documentservice.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByParentId(Long parentId);
    List<Folder> findByOwnerId(Long ownerId);
}


