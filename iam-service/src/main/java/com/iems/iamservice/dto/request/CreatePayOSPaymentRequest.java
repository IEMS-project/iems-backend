package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreatePayOSPaymentRequest {
    private String orderId;

    private String planId;

    @Positive(message = "Amount must be greater than 0")
    private Long amount;

    private String description;
}
