package com.iems.documentservice.repository;

import com.iems.documentservice.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {
    List<Favorite> findByUserId(UUID userId);
    Optional<Favorite> findByUserIdAndTargetId(UUID userId, UUID targetId);
    List<Favorite> findByUserIdAndTargetIdIn(UUID userId, Set<UUID> targetIds);
}



