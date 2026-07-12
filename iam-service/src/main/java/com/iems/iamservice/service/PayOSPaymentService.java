package com.iems.iamservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.iamservice.dto.request.CreatePayOSPaymentRequest;
import com.iems.iamservice.dto.response.PaymentTransactionResponse;
import com.iems.iamservice.dto.response.PayOSPaymentResponse;
import com.iems.iamservice.dto.response.PayOSPaymentStatusResponse;
import com.iems.iamservice.entity.Account;
import com.iems.iamservice.entity.PaymentTransaction;
import com.iems.iamservice.entity.SubscriptionPlan;
import com.iems.iamservice.entity.enums.PaymentStatus;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
import com.iems.iamservice.exception.InvalidPayOSWebhookException;
import com.iems.iamservice.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSPaymentService {

    private static final String SUCCESS_CODE = "00";

    private final PayOS payOS;
    private final AccountService accountService;
    private final SubscriptionPlanService subscriptionPlanService;
    private final ObjectMapper objectMapper;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EntityManager entityManager;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${payos.bank-name:}")
    private String bankName;

    @Value("${payos.account-name:}")
    private String accountName;

    @Value("${payos.account-number:}")
    private String accountNumber;

    /**
     * Creates pay ospayment data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param accountId the account id parameter
     * @param request the request parameter
     * @return the create payment result
     * @throws AppException if a business rule prevents the requested operation
     */
    @Transactional
    public PayOSPaymentResponse createPayment(UUID accountId, CreatePayOSPaymentRequest request) {
        Account account = accountService.findById(accountId);
        SubscriptionPlan plan = subscriptionPlanService.findActiveByCode(request.getPlanId());
        Long amount = plan.getPrice();
        if (amount == null || amount <= 0) {
            throw new AppException(ErrorCode.PAYMENT_INVALID_REQUEST);
        }

        Long orderCode = generateOrderCode();
        String description = normalizeDescription(request.getDescription(), orderCode, plan);
        String returnUrl = buildPaymentResultUrl("/payment/return", orderCode);
        String cancelUrl = buildPaymentResultUrl("/payment/cancel", orderCode);

        CreatePaymentLinkRequest paymentRequest = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .build();

        CreatePaymentLinkResponse payOSResponse;
        try {
            payOSResponse = payOS.paymentRequests().create(paymentRequest);
        } catch (PayOSException ex) {
            log.warn("Failed to create payOS payment link for accountId={}, planId={}, orderCode={}: {}",
                    account.getId(), plan.getCode(), orderCode, ex.getMessage());
            throw new AppException(ErrorCode.PAYMENT_CREATE_FAILED);
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
                .accountId(account.getId())
                .orderId(request.getOrderId())
                .planId(plan.getCode())
                .planName(plan.getName())
                .durationDays(plan.getDurationDays())
                .currency(plan.getCurrency())
                .priceAtPurchase(plan.getPrice())
                .orderCode(orderCode)
                .paymentLinkId(payOSResponse.getPaymentLinkId())
                .checkoutUrl(payOSResponse.getCheckoutUrl())
                .qrCode(resolveQrCode(payOSResponse))
                .bankName(bankName)
                .accountName(accountName)
                .accountNumber(accountNumber)
                .amount(amount)
                .amountPaid(0L)
                .amountRemaining(amount)
                .description(description)
                .status(PaymentStatus.PENDING)
                .build();

        PaymentTransaction saved = paymentTransactionRepository.save(transaction);
        log.info("Created payOS payment orderCode={}, paymentLinkId={}, status={}, accountId={}, planId={}",
                saved.getOrderCode(), saved.getPaymentLinkId(), saved.getStatus(), saved.getAccountId(), saved.getPlanId());

        return toCreateResponse(saved);
    }

    /**
     * Returns sync payment status for admin for pay ospayment processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param orderCode the order code parameter
     * @return the sync payment status for admin result
     * @throws AppException if a business rule prevents the requested operation
     */
    @Transactional
    public PayOSPaymentStatusResponse syncPaymentStatusForAdmin(Long orderCode) {
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND));

        PaymentStatus status = transaction.getStatus();
        if (status != PaymentStatus.PENDING && status != PaymentStatus.PROCESSING && status != PaymentStatus.FAILED) {
            throw new AppException(ErrorCode.PAYMENT_INVALID_STATE);
        }

        PaymentLink paymentLink;
        try {
            paymentLink = payOS.paymentRequests().get(orderCode);
        } catch (PayOSException ex) {
            log.warn("Failed to fetch payOS payment status for orderCode={}: {}", orderCode, ex.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        applyPaymentLinkStatus(transaction, paymentLink);
        return toStatusResponse(transaction);
    }

    /**
     * Returns cancel payment for admin for pay ospayment processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Persist the resulting domain changes.</li>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param orderCode the order code parameter
     * @param reason the reason parameter
     * @return the cancel payment for admin result
     * @throws AppException if a business rule prevents the requested operation
     */
    @Transactional
    public PayOSPaymentStatusResponse cancelPaymentForAdmin(Long orderCode, String reason) {
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND));

        if (transaction.getStatus() != PaymentStatus.PENDING) {
            throw new AppException(ErrorCode.PAYMENT_INVALID_STATE);
        }

        try {
            payOS.paymentRequests().cancel(orderCode, StringUtils.hasText(reason) ? reason : "Admin requested cancellation");
        } catch (PayOSException ex) {
            log.warn("Failed to cancel payOS payment link orderCode={}: {}", orderCode, ex.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        transaction.setStatus(PaymentStatus.CANCELLED);
        transaction.setCancelledAt(Instant.now());
        paymentTransactionRepository.save(transaction);
        return toStatusResponse(transaction);
    }

    /**
     * Searches pay ospayment information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param status the status parameter
     * @param accountId the account id parameter
     * @param planId the plan id parameter
     * @param from the from parameter
     * @param to the to parameter
     * @param keyword the keyword parameter
     * @param pageable the pageable parameter
     * @return the paginated result set
     */
    public Page<PaymentTransactionResponse> searchPayments(PaymentStatus status, UUID accountId, String planId,
                                                           Instant from, Instant to, String keyword, Pageable pageable) {
        String normalizedPlanId = StringUtils.hasText(planId) ? planId.trim().toLowerCase(Locale.ROOT) : null;
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<PaymentTransactionResponse> query = cb.createQuery(PaymentTransactionResponse.class);
        Root<PaymentTransaction> root = query.from(PaymentTransaction.class);
        query.select(cb.construct(
                PaymentTransactionResponse.class,
                root.get("id"),
                root.get("accountId"),
                root.get("orderId"),
                root.get("planId"),
                root.get("planName"),
                root.get("durationDays"),
                root.get("currency"),
                root.get("priceAtPurchase"),
                root.get("orderCode"),
                root.get("paymentLinkId"),
                root.get("checkoutUrl"),
                root.get("amount"),
                root.get("amountPaid"),
                root.get("amountRemaining"),
                root.get("status"),
                root.get("payosReference"),
                root.get("createdAt"),
                root.get("updatedAt"),
                root.get("paidAt"),
                root.get("cancelledAt")
        ));
        List<Predicate> predicates = buildPaymentPredicates(cb, root, status, accountId, normalizedPlanId, from, to);
        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }
        query.orderBy(cb.desc(root.get("createdAt")));

        List<PaymentTransactionResponse> content = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<PaymentTransaction> countRoot = countQuery.from(PaymentTransaction.class);
        countQuery.select(cb.count(countRoot));
        List<Predicate> countPredicates = buildPaymentPredicates(cb, countRoot, status, accountId, normalizedPlanId, from, to);
        if (!countPredicates.isEmpty()) {
            countQuery.where(countPredicates.toArray(Predicate[]::new));
        }
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Builds pay ospayment data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param cb the cb parameter
     * @param root the root parameter
     * @param status the status parameter
     * @param accountId the account id parameter
     * @param planId the plan id parameter
     * @param from the from parameter
     * @param to the to parameter
     * @return the matching result collection
     */
    private List<Predicate> buildPaymentPredicates(CriteriaBuilder cb, Root<PaymentTransaction> root,
                                                   PaymentStatus status, UUID accountId, String planId,
                                                   Instant from, Instant to) {
        List<Predicate> predicates = new ArrayList<>();
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        if (accountId != null) {
            predicates.add(cb.equal(root.get("accountId"), accountId));
        }
        if (StringUtils.hasText(planId)) {
            predicates.add(cb.equal(cb.lower(root.get("planId")), planId));
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return predicates;
    }

    /**
     * Retrieves pay ospayment information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param id the id parameter
     * @return the get payment result
     */
    public PaymentTransactionResponse getPayment(UUID id) {
        return paymentTransactionRepository.findAdminResponseById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND));
    }

    /**
     * Retrieves pay ospayment information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param orderCode the order code parameter
     * @param accountId the account id parameter
     * @return the get payment status result
     * @throws AppException if a business rule prevents the requested operation
     */
    @Transactional
    public PayOSPaymentStatusResponse getPaymentStatus(Long orderCode, UUID accountId) {
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND));

        if (!transaction.getAccountId().equals(accountId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (transaction.getStatus() == PaymentStatus.PAID) {
            return toStatusResponse(transaction);
        }

        if (transaction.getStatus() == PaymentStatus.CANCELLED) {
            return toStatusResponse(transaction);
        }

        PaymentLink paymentLink;
        try {
            paymentLink = payOS.paymentRequests().get(orderCode);
        } catch (PayOSException ex) {
            log.warn("Failed to fetch payOS payment status for orderCode={}: {}", orderCode, ex.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        applyPaymentLinkStatus(transaction, paymentLink);
        return toStatusResponse(transaction);
    }

    /**
     * Returns cancel payment for pay ospayment processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Persist the resulting domain changes.</li>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param orderCode the order code parameter
     * @param accountId the account id parameter
     * @param reason the reason parameter
     * @return the cancel payment result
     * @throws AppException if a business rule prevents the requested operation
     */
    @Transactional
    public PayOSPaymentStatusResponse cancelPayment(Long orderCode, UUID accountId, String reason) {
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND));

        if (!transaction.getAccountId().equals(accountId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (transaction.getStatus() == PaymentStatus.PAID) {
            return toStatusResponse(transaction);
        }

        if (transaction.getStatus() == PaymentStatus.CANCELLED) {
            return toStatusResponse(transaction);
        }

        try {
            if (StringUtils.hasText(reason)) {
                payOS.paymentRequests().cancel(orderCode, reason);
            } else {
                payOS.paymentRequests().cancel(orderCode);
            }
        } catch (PayOSException ex) {
            log.warn("Failed to cancel payOS payment link orderCode={}: {}", orderCode, ex.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        if (transaction.getStatus() != PaymentStatus.CANCELLED) {
            transaction.setStatus(PaymentStatus.CANCELLED);
            transaction.setCancelledAt(Instant.now());
            paymentTransactionRepository.save(transaction);
        }

        return toStatusResponse(transaction);
    }

    /**
     * Handles the pay ospayment operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Persist the resulting domain changes.</li>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param rawPayload the raw payload parameter
     * @throws InvalidPayOSWebhookException if the requested operation cannot be completed
     */
    @Transactional
    public void handleWebhook(String rawPayload) {
        Webhook webhook;
        WebhookData verifiedData;
        try {
            webhook = objectMapper.readValue(rawPayload, Webhook.class);
            verifiedData = payOS.webhooks().verify(rawPayload);
        } catch (Exception ex) {
            throw new InvalidPayOSWebhookException("Invalid payOS webhook signature or payload", ex);
        }

        Long orderCode = verifiedData.getOrderCode();
        if (orderCode == null) {
            log.warn("Verified payOS webhook without orderCode");
            return;
        }

        PaymentTransaction transaction = paymentTransactionRepository.findByOrderCodeForUpdate(orderCode)
                .orElse(null);
        if (transaction == null) {
            log.warn("Verified payOS webhook for unknown orderCode={}", orderCode);
            return;
        }

        boolean alreadyPaid = transaction.getStatus() == PaymentStatus.PAID;
        updateWebhookMetadata(transaction, verifiedData, rawPayload);

        if (alreadyPaid) {
            paymentTransactionRepository.save(transaction);
            log.info("Ignored duplicate paid payOS webhook orderCode={}, paymentLinkId={}, status={}",
                    orderCode, transaction.getPaymentLinkId(), transaction.getStatus());
            return;
        }

        if (isSuccessfulPayment(webhook, verifiedData)) {
            transaction.setStatus(PaymentStatus.PAID);
            transaction.setPaidAt(Instant.now());
            if (transaction.getDurationDays() != null && transaction.getDurationDays() > 0) {
                accountService.upgradeToPremium(transaction.getAccountId(), transaction.getDurationDays());
            }
        } else if (isCancellation(webhook, verifiedData)) {
            transaction.setStatus(PaymentStatus.CANCELLED);
            transaction.setCancelledAt(Instant.now());
        } else {
            transaction.setStatus(PaymentStatus.FAILED);
        }

        paymentTransactionRepository.save(transaction);
        log.info("Processed payOS webhook orderCode={}, paymentLinkId={}, status={}",
                transaction.getOrderCode(), transaction.getPaymentLinkId(), transaction.getStatus());
    }

    /**
     * Generates pay ospayment data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @return the generate order code result
     */
    private Long generateOrderCode() {
        long candidate = System.currentTimeMillis();
        while (paymentTransactionRepository.existsByOrderCode(candidate)) {
            candidate++;
        }
        return candidate;
    }

    /**
     * Normalizes pay ospayment content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param description the description parameter
     * @param orderCode the order code parameter
     * @param plan the plan parameter
     * @return the normalize description result
     */
    private String normalizeDescription(String description, Long orderCode, SubscriptionPlan plan) {
        String base = StringUtils.hasText(description)
                ? description
                : plan.getCode().toUpperCase(Locale.ROOT);
        String full = base + "-" + orderCode;
        if (full.length() > 25) {
            return full.substring(0, 25);
        }
        return full;
    }

    /**
     * Builds pay ospayment data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param path the path parameter
     * @param orderCode the order code parameter
     * @return the build payment result url result
     */
    private String buildPaymentResultUrl(String path, Long orderCode) {
        String normalizedFrontendUrl = StringUtils.trimTrailingCharacter(frontendUrl, '/');
        return normalizedFrontendUrl + path + "?orderCode=" + orderCode;
    }

    /**
     * Returns is successful payment for pay ospayment processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param webhook the webhook parameter
     * @param data the data parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isSuccessfulPayment(Webhook webhook, WebhookData data) {
        return Boolean.TRUE.equals(webhook.getSuccess()) && SUCCESS_CODE.equals(data.getCode());
    }

    /**
     * Returns is cancellation for pay ospayment processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param webhook the webhook parameter
     * @param data the data parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isCancellation(Webhook webhook, WebhookData data) {
        return isCancelledValue(webhook.getCode())
                || isCancelledValue(data.getCode())
                || isCancelledValue(data.getDesc());
    }

    /**
     * Returns is cancelled value for pay ospayment processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isCancelledValue(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("cancel") || normalized.contains("huy") || normalized.contains("hủy");
    }

    /**
     * Resolves pay ospayment information for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param response the response parameter
     * @return the resolve qr code result
     */
    private String resolveQrCode(CreatePaymentLinkResponse response) {
        if (StringUtils.hasText(response.getQrCode())) {
            return response.getQrCode();
        }
        return response.getCheckoutUrl();
    }

    /**
     * Applies pay ospayment changes.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param transaction the transaction parameter
     * @param paymentLink the payment link parameter
     */
    private void applyPaymentLinkStatus(PaymentTransaction transaction, PaymentLink paymentLink) {
        if (paymentLink == null) {
            return;
        }

        if (StringUtils.hasText(paymentLink.getId())) {
            transaction.setPaymentLinkId(paymentLink.getId());
        }
        transaction.setAmountPaid(paymentLink.getAmountPaid());
        transaction.setAmountRemaining(paymentLink.getAmountRemaining());

        PaymentStatus nextStatus = mapPayOSStatus(paymentLink.getStatus());
        if (transaction.getStatus() == PaymentStatus.PAID) {
            return;
        }

        if (nextStatus == PaymentStatus.PAID) {
            transaction.setStatus(PaymentStatus.PAID);
            transaction.setPaidAt(Instant.now());
            if (transaction.getDurationDays() != null && transaction.getDurationDays() > 0) {
                accountService.upgradeToPremium(transaction.getAccountId(), transaction.getDurationDays());
            }
        } else if (nextStatus == PaymentStatus.CANCELLED) {
            transaction.setStatus(PaymentStatus.CANCELLED);
            transaction.setCancelledAt(Instant.now());
        } else if (nextStatus != null) {
            transaction.setStatus(nextStatus);
        }

        paymentTransactionRepository.save(transaction);
    }

    /**
     * Maps pay ospayment data to the target representation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param status the status parameter
     * @return the map pay osstatus result
     */
    private PaymentStatus mapPayOSStatus(PaymentLinkStatus status) {
        if (status == null) {
            return PaymentStatus.PROCESSING;
        }
        return switch (status) {
            case PAID -> PaymentStatus.PAID;
            case CANCELLED -> PaymentStatus.CANCELLED;
            case FAILED, EXPIRED -> PaymentStatus.FAILED;
            case PROCESSING, UNDERPAID -> PaymentStatus.PROCESSING;
            case PENDING -> PaymentStatus.PENDING;
        };
    }

    /**
     * Updates pay ospayment data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param transaction the transaction parameter
     * @param data the data parameter
     * @param rawPayload the raw payload parameter
     */
    private void updateWebhookMetadata(PaymentTransaction transaction, WebhookData data, String rawPayload) {
        if (StringUtils.hasText(data.getPaymentLinkId())) {
            transaction.setPaymentLinkId(data.getPaymentLinkId());
        }
        transaction.setPayosReference(data.getReference());
        transaction.setRawWebhookJson(rawPayload);
    }

    /**
     * Returns to create response for pay ospayment processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param transaction the transaction parameter
     * @return the to create response result
     */
    private PayOSPaymentResponse toCreateResponse(PaymentTransaction transaction) {
        return PayOSPaymentResponse.builder()
                .orderCode(transaction.getOrderCode())
                .orderId(transaction.getOrderId())
                .checkoutUrl(transaction.getCheckoutUrl())
                .paymentLinkId(transaction.getPaymentLinkId())
                .qrCode(transaction.getQrCode())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .description(transaction.getDescription())
                .bankName(transaction.getBankName())
                .accountName(transaction.getAccountName())
                .accountNumber(transaction.getAccountNumber())
                .amountPaid(transaction.getAmountPaid())
                .amountRemaining(transaction.getAmountRemaining())
                .status(transaction.getStatus())
                .build();
    }

    /**
     * Returns to status response for pay ospayment processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param transaction the transaction parameter
     * @return the to status response result
     */
    private PayOSPaymentStatusResponse toStatusResponse(PaymentTransaction transaction) {
        return PayOSPaymentStatusResponse.builder()
                .orderCode(transaction.getOrderCode())
                .orderId(transaction.getOrderId())
                .status(transaction.getStatus())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .amountPaid(transaction.getAmountPaid())
                .amountRemaining(transaction.getAmountRemaining())
                .paymentLinkId(transaction.getPaymentLinkId())
                .qrCode(transaction.getQrCode())
                .description(transaction.getDescription())
                .bankName(transaction.getBankName())
                .accountName(transaction.getAccountName())
                .accountNumber(transaction.getAccountNumber())
                .accountId(transaction.getAccountId())
                .planId(transaction.getPlanId())
                .build();
    }

    /**
     * Returns to payment transaction response for pay ospayment processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param transaction the transaction parameter
     * @return the to payment transaction response result
     */
    private PaymentTransactionResponse toPaymentTransactionResponse(PaymentTransaction transaction) {
        return PaymentTransactionResponse.builder()
                .id(transaction.getId())
                .accountId(transaction.getAccountId())
                .orderId(transaction.getOrderId())
                .planId(transaction.getPlanId())
                .planName(transaction.getPlanName())
                .durationDays(transaction.getDurationDays())
                .currency(transaction.getCurrency())
                .priceAtPurchase(transaction.getPriceAtPurchase())
                .orderCode(transaction.getOrderCode())
                .paymentLinkId(transaction.getPaymentLinkId())
                .checkoutUrl(transaction.getCheckoutUrl())
                .amount(transaction.getAmount())
                .amountPaid(transaction.getAmountPaid())
                .amountRemaining(transaction.getAmountRemaining())
                .description(transaction.getDescription())
                .status(transaction.getStatus())
                .payosReference(transaction.getPayosReference())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .paidAt(transaction.getPaidAt())
                .cancelledAt(transaction.getCancelledAt())
                .build();
    }
}
