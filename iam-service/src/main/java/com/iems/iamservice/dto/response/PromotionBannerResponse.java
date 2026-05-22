package com.iems.iamservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PromotionBannerResponse {
    private UUID id;
    private String title;
    private String description;
    private String imageUrl;
    private String ctaLabel;
    private String ctaUrl;
    private String placement;
    private Integer priority;
    private Boolean active;
    private Instant startsAt;
    private Instant endsAt;
    private Instant createdAt;
    private Instant updatedAt;
}
