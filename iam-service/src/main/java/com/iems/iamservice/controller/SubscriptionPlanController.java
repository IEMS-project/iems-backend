package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.response.SubscriptionPlanResponse;
import com.iems.iamservice.service.SubscriptionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscription-plans")
@RequiredArgsConstructor
@Tag(name = "Subscription Plans", description = "Client APIs for active subscription plans")
public class SubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    @GetMapping("/active")
    @Operation(summary = "List active subscription plans")
    public ResponseEntity<ApiResponseDto<List<SubscriptionPlanResponse>>> listActive() {
        return ResponseEntity.ok(ApiResponseDto.<List<SubscriptionPlanResponse>>builder()
                .status("success")
                .message("Active subscription plans retrieved successfully")
                .data(subscriptionPlanService.listActive())
                .build());
    }
}
