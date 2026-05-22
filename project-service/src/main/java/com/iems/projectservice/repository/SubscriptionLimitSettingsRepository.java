package com.iems.projectservice.repository;

import com.iems.projectservice.entity.SubscriptionLimitSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionLimitSettingsRepository extends JpaRepository<SubscriptionLimitSettings, String> {
}
