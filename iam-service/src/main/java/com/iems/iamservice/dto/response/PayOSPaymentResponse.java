package com.iems.iamservice.dto.response;

import com.iems.iamservice.entity.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayOSPaymentResponse {
    private Long orderCode;
    private String orderId;
    private String checkoutUrl;
    private String paymentLinkId;
    private String qrCode;
    private Long amount;
    private String currency;
    private String description;
    private String bankName;
    private String accountName;
    private String accountNumber;
    private Long amountPaid;
    private Long amountRemaining;
    private PaymentStatus status;
}
