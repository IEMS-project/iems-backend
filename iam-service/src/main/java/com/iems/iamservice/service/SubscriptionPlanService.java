package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.SubscriptionPlanRequest;
import com.iems.iamservice.dto.response.SubscriptionPlanResponse;
import com.iems.iamservice.entity.SubscriptionPlan;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
import com.iems.iamservice.repository.PaymentTransactionRepository;
import com.iems.iamservice.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Transactional
    public void ensureDefaultPlans() {
        seedPlanIfMissing("week", "Week", "Quick trial for individuals or small teams.", 49_000L, 7, false, 10);
        seedPlanIfMissing("month", "Month", "Good fit for a sprint or short project.", 149_000L, 30, true, 20);
        seedPlanIfMissing("year", "Year", "Best value for teams using IEMS long term.", 1_499_000L, 365, false, 30);
    }

    private void seedPlanIfMissing(String code, String name, String description, long price,
                                   int durationDays, boolean recommended, int sortOrder) {
        if (subscriptionPlanRepository.existsByCodeIgnoreCase(code)) {
            return;
        }
        subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .code(code)
                .name(name)
                .description(description)
                .price(price)
                .durationDays(durationDays)
                .currency("VND")
                .active(true)
                .recommended(recommended)
                .sortOrder(sortOrder)
                .build());
    }

    public List<SubscriptionPlanResponse> listAll() {
        return List.of(
                findPlanResponseOrDefault("week"),
                findPlanResponseOrDefault("month"),
                findPlanResponseOrDefault("year")
        );
    }

    public List<SubscriptionPlanResponse> listActive() {
        return listAll().stream()
                .filter(plan -> Boolean.TRUE.equals(plan.getActive()))
                .toList();
    }

    public SubscriptionPlan findActiveByCode(String code) {
        ensureDefaultPlans();
        return subscriptionPlanRepository.findByCodeIgnoreCase(normalizeCode(code))
                .filter(plan -> Boolean.TRUE.equals(plan.getActive()))
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_INVALID_PLAN));
    }

    public SubscriptionPlan findByCode(String code) {
        ensureDefaultPlans();
        return subscriptionPlanRepository.findByCodeIgnoreCase(normalizeCode(code))
                .orElseThrow(() -> new AppException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    @Transactional
    public SubscriptionPlanResponse create(SubscriptionPlanRequest request) {
        String code = normalizeCode(request.getCode());
        SubscriptionPlan plan = subscriptionPlanRepository.findByCodeIgnoreCase(code)
                .orElseGet(SubscriptionPlan::new);
        apply(plan, request, code);
        return toResponse(subscriptionPlanRepository.save(plan));
    }

    @Transactional
    public SubscriptionPlanResponse update(UUID id, SubscriptionPlanRequest request) {
        SubscriptionPlan plan = getEntity(id);
        String code = normalizeCode(request.getCode());
        subscriptionPlanRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.SUBSCRIPTION_PLAN_CODE_EXISTS);
                });
        apply(plan, request, code);
        return toResponse(subscriptionPlanRepository.save(plan));
    }

    @Transactional
    public SubscriptionPlanResponse setActive(UUID id, boolean active) {
        SubscriptionPlan plan = getEntity(id);
        plan.setActive(active);
        return toResponse(subscriptionPlanRepository.save(plan));
    }

    @Transactional
    public void delete(UUID id) {
        SubscriptionPlan plan = getEntity(id);
        if (paymentTransactionRepository.existsByPlanId(plan.getCode())) {
            plan.setActive(false);
            subscriptionPlanRepository.save(plan);
            return;
        }
        subscriptionPlanRepository.delete(plan);
    }

    private SubscriptionPlan getEntity(UUID id) {
        ensureDefaultPlans();
        return subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    private void apply(SubscriptionPlan plan, SubscriptionPlanRequest request, String code) {
        plan.setCode(code);
        plan.setName(request.getName().trim());
        plan.setDescription(request.getDescription());
        plan.setPrice(request.getPrice());
        plan.setDurationDays(request.getDurationDays());
        plan.setCurrency(StringUtils.hasText(request.getCurrency()) ? request.getCurrency().trim().toUpperCase() : "VND");
        plan.setActive(request.getActive() == null || request.getActive());
        plan.setRecommended(Boolean.TRUE.equals(request.getRecommended()));
        plan.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new AppException(ErrorCode.PAYMENT_INVALID_PLAN);
        }
        return code.trim().toLowerCase();
    }

    public SubscriptionPlanResponse toResponse(SubscriptionPlan plan) {
        return SubscriptionPlanResponse.builder()
                .id(plan.getId()).code(plan.getCode()).name(plan.getName()).description(plan.getDescription())
                .price(plan.getPrice()).durationDays(plan.getDurationDays()).currency(plan.getCurrency())
                .active(plan.getActive()).recommended(plan.getRecommended()).sortOrder(plan.getSortOrder())
                .features(null).createdAt(plan.getCreatedAt()).updatedAt(plan.getUpdatedAt())
                .build();
    }

    private List<SubscriptionPlanResponse> defaultPlanResponses() {
        return List.of(
                defaultPlanResponse("week", "Week", "Quick trial for individuals or small teams.", 49_000L, 7, false, 10),
                defaultPlanResponse("month", "Month", "Good fit for a sprint or short project.", 149_000L, 30, true, 20),
                defaultPlanResponse("year", "Year", "Best value for teams using IEMS long term.", 1_499_000L, 365, false, 30)
        );
    }

    private SubscriptionPlanResponse findPlanResponseOrDefault(String code) {
        try {
            Optional<SubscriptionPlan> plan = subscriptionPlanRepository.findByCodeIgnoreCase(code);
            return plan.map(this::toResponse).orElseGet(() -> defaultPlanResponseFor(code));
        } catch (DataAccessException ex) {
            log.error("Failed to load subscription plan code={}. Returning default price for that plan.", code, ex);
            return defaultPlanResponseFor(code);
        }
    }

    private SubscriptionPlanResponse defaultPlanResponseFor(String code) {
        return switch (code) {
            case "week" -> defaultPlanResponse("week", "Week", "Quick trial for individuals or small teams.", 49_000L, 7, false, 10);
            case "month" -> defaultPlanResponse("month", "Month", "Good fit for a sprint or short project.", 149_000L, 30, true, 20);
            case "year" -> defaultPlanResponse("year", "Year", "Best value for teams using IEMS long term.", 1_499_000L, 365, false, 30);
            default -> throw new AppException(ErrorCode.PAYMENT_INVALID_PLAN);
        };
    }

    private SubscriptionPlanResponse defaultPlanResponse(String code, String name, String description, long price,
                                                         int durationDays, boolean recommended, int sortOrder) {
        return SubscriptionPlanResponse.builder()
                .code(code)
                .name(name)
                .description(description)
                .price(price)
                .durationDays(durationDays)
                .currency("VND")
                .active(true)
                .recommended(recommended)
                .sortOrder(sortOrder)
                .features(null)
                .build();
    }
}
