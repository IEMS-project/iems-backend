package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class SubscriptionPlanRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String description;

    @NotNull
    @Positive
    private Long price;

    @NotNull
    @Positive
    private Integer durationDays;

    private String currency = "VND";

    private Boolean active = true;

    private Boolean recommended = false;

    @Min(0)
    private Integer sortOrder = 0;

    private String features;
}
