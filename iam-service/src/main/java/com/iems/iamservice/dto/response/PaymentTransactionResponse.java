package com.iems.iamservice.dto.response;

import com.iems.iamservice.entity.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionResponse {
    private UUID id;
    private UUID accountId;
    private String orderId;
    private String planId;
    private String planName;
    private Integer durationDays;
    private String currency;
    private Long priceAtPurchase;
    private Long orderCode;
    private String paymentLinkId;
    private String checkoutUrl;
    private Long amount;
    private Long amountPaid;
    private Long amountRemaining;
    private String description;
    private PaymentStatus status;
    private String payosReference;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant paidAt;
    private Instant cancelledAt;

    public PaymentTransactionResponse(UUID id, UUID accountId, String orderId, String planId, String planName,
                                      Integer durationDays, String currency, Long priceAtPurchase, Long orderCode,
                                      String paymentLinkId, String checkoutUrl, Long amount, Long amountPaid,
                                      Long amountRemaining, PaymentStatus status, String payosReference,
                                      Instant createdAt, Instant updatedAt, Instant paidAt, Instant cancelledAt) {
        this.id = id;
        this.accountId = accountId;
        this.orderId = orderId;
        this.planId = planId;
        this.planName = planName;
        this.durationDays = durationDays;
        this.currency = currency;
        this.priceAtPurchase = priceAtPurchase;
        this.orderCode = orderCode;
        this.paymentLinkId = paymentLinkId;
        this.checkoutUrl = checkoutUrl;
        this.amount = amount;
        this.amountPaid = amountPaid;
        this.amountRemaining = amountRemaining;
        this.status = status;
        this.payosReference = payosReference;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.paidAt = paidAt;
        this.cancelledAt = cancelledAt;
    }
}
