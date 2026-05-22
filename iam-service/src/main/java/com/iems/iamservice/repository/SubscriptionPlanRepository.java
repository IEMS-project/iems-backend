package com.iems.iamservice.repository;

import com.iems.iamservice.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    Optional<SubscriptionPlan> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<SubscriptionPlan> findByActiveTrueOrderBySortOrderAscPriceAsc();

    List<SubscriptionPlan> findAllByOrderBySortOrderAscPriceAsc();
}
