package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class PromotionBannerRequest {
    @NotBlank
    private String title;

    private String description;

    private String imageUrl;

    private String ctaLabel;

    private String ctaUrl;

    @NotBlank
    private String placement;

    @NotNull
    private Integer priority = 0;

    private Boolean active = true;

    private Instant startsAt;

    private Instant endsAt;
}
