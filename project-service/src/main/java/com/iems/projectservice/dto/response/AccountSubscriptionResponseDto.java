package com.iems.projectservice.dto.response;

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
    private String subscriptionType;
    private Instant premiumUntil;
}
