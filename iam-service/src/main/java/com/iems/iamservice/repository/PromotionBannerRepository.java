package com.iems.iamservice.repository;

import com.iems.iamservice.entity.PromotionBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PromotionBannerRepository extends JpaRepository<PromotionBanner, UUID> {
    List<PromotionBanner> findAllByOrderByPriorityDescCreatedAtDesc();

    @Query("""
            select p from PromotionBanner p
            where p.active = true
              and upper(p.placement) = upper(:placement)
              and (p.startsAt is null or p.startsAt <= :now)
              and (p.endsAt is null or p.endsAt >= :now)
            order by p.priority desc, p.createdAt desc
            """)
    List<PromotionBanner> findActiveByPlacement(@Param("placement") String placement, @Param("now") Instant now);
}
