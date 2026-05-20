package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpgradeSubscriptionDto {

    /**
     * Number of days for the premium subscription.
     * E.g. 30 = 1 month, 365 = 1 year.
     */
    @NotNull(message = "Duration days is required")
    @Min(value = 1, message = "Duration must be at least 1 day")
    private Integer durationDays;
}
