package com.iems.iamservice.dto.response;

import com.iems.iamservice.entity.enums.SubscriptionType;
import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
public class AccountResponseDto {
    private UUID id;
    private UUID userId;
    private String username;
    private String email;
    private Boolean enabled;
    private Instant createdAt;
    private Set<String> roles;
    private Set<String> permissions;
    private SubscriptionType subscriptionType;
    private Instant premiumUntil;
}


