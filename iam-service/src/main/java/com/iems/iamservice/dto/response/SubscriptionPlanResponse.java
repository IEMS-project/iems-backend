package com.iems.iamservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SubscriptionPlanResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private Long price;
    private Integer durationDays;
    private String currency;
    private Boolean active;
    private Boolean recommended;
    private Integer sortOrder;
    private String features;
    private Instant createdAt;
    private Instant updatedAt;
}
