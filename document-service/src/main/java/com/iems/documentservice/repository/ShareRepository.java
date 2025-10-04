package com.iems.documentservice.repository;

import com.iems.documentservice.entity.Share;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShareRepository extends JpaRepository<Share, UUID> {
    List<Share> findByTargetIdAndTargetType(UUID targetId, String targetType);
    List<Share> findBySharedWithUserId(UUID sharedWithUserId);
    boolean existsByTargetIdAndTargetTypeAndSharedWithUserId(UUID targetId, String targetType, UUID sharedWithUserId);
    void deleteByTargetIdAndTargetTypeAndSharedWithUserId(UUID targetId, String targetType, UUID sharedWithUserId);
}
