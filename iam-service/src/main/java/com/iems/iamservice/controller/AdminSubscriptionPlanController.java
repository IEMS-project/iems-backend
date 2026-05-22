package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.SubscriptionPlanRequest;
import com.iems.iamservice.dto.request.ToggleActiveRequest;
import com.iems.iamservice.dto.response.SubscriptionPlanResponse;
import com.iems.iamservice.service.SubscriptionPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/subscription-plans")
@RequiredArgsConstructor
@Tag(name = "Admin Subscription Plans", description = "Admin APIs for subscription plan management")
public class AdminSubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<SubscriptionPlanResponse>>> list() {
        return ResponseEntity.ok(ApiResponseDto.<List<SubscriptionPlanResponse>>builder()
                .status("success").message("Subscription plans retrieved successfully")
                .data(subscriptionPlanService.listAll()).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<SubscriptionPlanResponse>> create(@Valid @RequestBody SubscriptionPlanRequest request) {
        return ResponseEntity.ok(ApiResponseDto.<SubscriptionPlanResponse>builder()
                .status("success").message("Subscription plan created successfully")
                .data(subscriptionPlanService.create(request)).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<SubscriptionPlanResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody SubscriptionPlanRequest request
    ) {
        return ResponseEntity.ok(ApiResponseDto.<SubscriptionPlanResponse>builder()
                .status("success").message("Subscription plan updated successfully")
                .data(subscriptionPlanService.update(id, request)).build());
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponseDto<SubscriptionPlanResponse>> setActive(
            @PathVariable UUID id,
            @Valid @RequestBody ToggleActiveRequest request
    ) {
        return ResponseEntity.ok(ApiResponseDto.<SubscriptionPlanResponse>builder()
                .status("success").message("Subscription plan status updated successfully")
                .data(subscriptionPlanService.setActive(id, request.getActive())).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable UUID id) {
        subscriptionPlanService.delete(id);
        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success").message("Subscription plan deleted or deactivated successfully").build());
    }
}
