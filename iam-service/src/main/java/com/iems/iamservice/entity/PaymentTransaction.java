package com.iems.iamservice.entity;

import com.iems.iamservice.entity.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payment_transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_transactions_order_code", columnNames = "order_code")
        },
        indexes = {
                @Index(name = "idx_payment_transactions_created_at", columnList = "created_at"),
                @Index(name = "idx_payment_transactions_status", columnList = "status"),
                @Index(name = "idx_payment_transactions_account_id", columnList = "account_id"),
                @Index(name = "idx_payment_transactions_plan_id", columnList = "plan_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "order_id", length = 80)
    private String orderId;

    @Column(name = "plan_id", length = 30)
    private String planId;

    @Column(name = "plan_name", length = 120)
    private String planName;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "price_at_purchase")
    private Long priceAtPurchase;

    @Column(name = "order_code", nullable = false, unique = true)
    private Long orderCode;

    @Column(name = "payment_link_id", length = 100)
    private String paymentLinkId;

    @Column(name = "checkout_url", length = 1024)
    private String checkoutUrl;

    @Lob
    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "account_name", length = 120)
    private String accountName;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "amount_paid")
    private Long amountPaid;

    @Column(name = "amount_remaining")
    private Long amountRemaining;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "payos_reference", length = 100)
    private String payosReference;

    @Lob
    @Column(name = "raw_webhook_json", columnDefinition = "TEXT")
    private String rawWebhookJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private Instant paidAt;

    @Column
    private Instant cancelledAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
