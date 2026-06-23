package com.iems.iamservice.dto.response;

import com.iems.iamservice.entity.enums.SubscriptionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountSubscriptionResponseDto {
    private UUID accountId;
    private SubscriptionType subscriptionType;
    private Instant premiumUntil;
}
