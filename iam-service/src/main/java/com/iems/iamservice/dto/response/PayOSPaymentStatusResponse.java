package com.iems.iamservice.dto.response;

import com.iems.iamservice.entity.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PayOSPaymentStatusResponse {
    private Long orderCode;
    private String orderId;
    private PaymentStatus status;
    private Long amount;
    private String currency;
    private Long amountPaid;
    private Long amountRemaining;
    private String paymentLinkId;
    private String qrCode;
    private String description;
    private String bankName;
    private String accountName;
    private String accountNumber;
    private UUID accountId;
    private String planId;
}
