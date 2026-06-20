package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.SubscriptionPlanRequest;
import com.iems.iamservice.dto.response.SubscriptionPlanResponse;
import com.iems.iamservice.entity.SubscriptionPlan;
import com.iems.iamservice.repository.PaymentTransactionRepository;
import com.iems.iamservice.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionPlanServiceTest {

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    private SubscriptionPlanService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPlanService(subscriptionPlanRepository, paymentTransactionRepository);
    }

    @Test
    void createShouldNormalizeAndPersistPlan() {
        SubscriptionPlanRequest request = planRequest();
        SubscriptionPlan saved = SubscriptionPlan.builder().id(UUID.randomUUID()).code("premium").name("Premium").build();
        when(subscriptionPlanRepository.findByCodeIgnoreCase("premium")).thenReturn(Optional.empty());
        when(subscriptionPlanRepository.save(any(SubscriptionPlan.class))).thenReturn(saved);

        SubscriptionPlanResponse response = service.create(request);

        assertEquals("premium", response.getCode());
        assertEquals("Premium", response.getName());
    }

    @Test
    void updateShouldRejectDuplicateCode() {
        UUID id = UUID.randomUUID();
        SubscriptionPlan existing = SubscriptionPlan.builder().id(UUID.randomUUID()).code("premium").build();
        when(subscriptionPlanRepository.findById(id)).thenReturn(Optional.of(SubscriptionPlan.builder().id(id).code("basic").build()));
        when(subscriptionPlanRepository.findByCodeIgnoreCase("premium")).thenReturn(Optional.of(existing));

        assertThrows(RuntimeException.class, () -> service.update(id, planRequest()));
    }

    @Test
    void deleteShouldDeactivateWhenPlanInUse() {
        UUID id = UUID.randomUUID();
        SubscriptionPlan plan = SubscriptionPlan.builder().id(id).code("premium").active(true).build();
        when(subscriptionPlanRepository.findById(id)).thenReturn(Optional.of(plan));
        when(paymentTransactionRepository.existsByPlanId("premium")).thenReturn(true);

        service.delete(id);

        assertFalse(plan.getActive());
        verify(subscriptionPlanRepository).save(plan);
    }

    @Test
    void listActiveShouldMapRepositoryResults() {
        SubscriptionPlan plan = SubscriptionPlan.builder().id(UUID.randomUUID()).code("premium").name("Premium").active(true).price(99_000L).durationDays(30).build();

        List<SubscriptionPlanResponse> result = service.listActive();

        assertEquals(3, result.size());
        assertEquals(List.of("week", "month", "year"), result.stream().map(SubscriptionPlanResponse::getCode).toList());
    }

    private SubscriptionPlanRequest planRequest() {
        SubscriptionPlanRequest request = new SubscriptionPlanRequest();
        request.setCode("Premium");
        request.setName("Premium");
        request.setDescription("Plan");
        request.setPrice(99_000L);
        request.setDurationDays(30);
        request.setCurrency("vnd");
        request.setActive(true);
        request.setRecommended(false);
        request.setSortOrder(1);
        return request;
    }
}