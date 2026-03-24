package com.iems.documentservice.repository;

import com.iems.documentservice.entity.DocumentActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentActivityRepository extends JpaRepository<DocumentActivity, UUID> {
    List<DocumentActivity> findByTargetIdAndTargetTypeOrderByCreatedAtDesc(UUID targetId, String targetType);
}
