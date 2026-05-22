package com.iems.iamservice.repository;

import com.iems.iamservice.entity.PaymentTransaction;
import com.iems.iamservice.dto.response.PaymentTransactionResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByOrderCode(Long orderCode);

    boolean existsByOrderCode(Long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PaymentTransaction pt where pt.orderCode = :orderCode")
    Optional<PaymentTransaction> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    boolean existsByPlanId(String planId);

    @Query(
            value = """
            select new com.iems.iamservice.dto.response.PaymentTransactionResponse(
                pt.id, pt.accountId, pt.orderId, pt.planId, pt.planName,
                pt.durationDays, pt.currency, pt.priceAtPurchase, pt.orderCode,
                pt.paymentLinkId, pt.checkoutUrl, pt.amount, pt.amountPaid,
                pt.amountRemaining, pt.status, pt.payosReference,
                pt.createdAt, pt.updatedAt, pt.paidAt, pt.cancelledAt
            )
            from PaymentTransaction pt
            where (:status is null or pt.status = :status)
              and (:accountId is null or pt.accountId = :accountId)
              and (:planId is null or pt.planId = :planId)
              and (:from is null or pt.createdAt >= :from)
              and (:to is null or pt.createdAt <= :to)
            """,
            countQuery = """
            select count(pt)
            from PaymentTransaction pt
            where (:status is null or pt.status = :status)
              and (:accountId is null or pt.accountId = :accountId)
              and (:planId is null or pt.planId = :planId)
              and (:from is null or pt.createdAt >= :from)
              and (:to is null or pt.createdAt <= :to)
            """
    )
    Page<PaymentTransactionResponse> searchAdmin(
            @Param("status") com.iems.iamservice.entity.enums.PaymentStatus status,
            @Param("accountId") UUID accountId,
            @Param("planId") String planId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("""
            select new com.iems.iamservice.dto.response.PaymentTransactionResponse(
                pt.id, pt.accountId, pt.orderId, pt.planId, pt.planName,
                pt.durationDays, pt.currency, pt.priceAtPurchase, pt.orderCode,
                pt.paymentLinkId, pt.checkoutUrl, pt.amount, pt.amountPaid,
                pt.amountRemaining, pt.status, pt.payosReference,
                pt.createdAt, pt.updatedAt, pt.paidAt, pt.cancelledAt
            )
            from PaymentTransaction pt
            where pt.id = :id
            """)
    Optional<PaymentTransactionResponse> findAdminResponseById(@Param("id") UUID id);
}
