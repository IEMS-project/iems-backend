package com.iems.documentservice.repository;

import com.iems.documentservice.entity.FileShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileShareRepository extends JpaRepository<FileShare, Long> {
    List<FileShare> findByFileId(Long fileId);
    List<FileShare> findBySharedWithUserId(Long userId);
    boolean existsByFileIdAndSharedWithUserId(Long fileId, Long userId);
    void deleteByFileIdAndSharedWithUserId(Long fileId, Long userId);
}



