package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.CreatePayOSPaymentRequest;
import com.iems.iamservice.dto.response.PayOSPaymentResponse;
import com.iems.iamservice.dto.response.PayOSPaymentStatusResponse;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
import com.iems.iamservice.exception.InvalidPayOSWebhookException;
import com.iems.iamservice.security.JwtUserDetails;
import com.iems.iamservice.service.PayOSPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments/payos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "payOS Payments", description = "APIs for Premium payOS checkout and webhook handling")
public class PayOSPaymentController {

    private final PayOSPaymentService payOSPaymentService;

    @PostMapping("/create")
    @Operation(summary = "Create payOS payment link", description = "Create a payOS checkout link for a Premium plan")
    public ResponseEntity<ApiResponseDto<PayOSPaymentResponse>> createPayment(
            @Valid @RequestBody CreatePayOSPaymentRequest request,
            Authentication authentication
    ) {
        UUID accountId = resolveAccountId(authentication);
        PayOSPaymentResponse response = payOSPaymentService.createPayment(accountId, request);

        return ResponseEntity.ok(ApiResponseDto.<PayOSPaymentResponse>builder()
                .status("success")
                .message("payOS payment link created successfully")
                .data(response)
                .build());
    }

    @GetMapping("/status/{orderCode}")
    @Operation(summary = "Get payOS payment status", description = "Get the stored payment status from backend database")
    public ResponseEntity<ApiResponseDto<PayOSPaymentStatusResponse>> getPaymentStatus(
            @PathVariable Long orderCode,
            Authentication authentication
    ) {
        UUID accountId = resolveAccountId(authentication);
        PayOSPaymentStatusResponse response = payOSPaymentService.getPaymentStatus(orderCode, accountId);

        return ResponseEntity.ok(ApiResponseDto.<PayOSPaymentStatusResponse>builder()
                .status("success")
                .message("Payment status retrieved successfully")
                .data(response)
                .build());
    }

    @PostMapping("/webhook")
    @Operation(summary = "payOS webhook", description = "Verify and process payOS payment webhooks")
    public ResponseEntity<String> handleWebhook(@RequestBody String rawPayload) {
        try {
            payOSPaymentService.handleWebhook(rawPayload);
            return ResponseEntity.ok("OK");
        } catch (InvalidPayOSWebhookException ex) {
            log.warn("Rejected invalid payOS webhook: {}", ex.getMessage());
            return ResponseEntity.badRequest().body("Invalid webhook");
        }
    }

    @PostMapping("/cancel/{orderCode}")
    @Operation(summary = "Cancel payOS payment link", description = "Cancel a payOS payment link if it has not been paid")
    public ResponseEntity<ApiResponseDto<PayOSPaymentStatusResponse>> cancelPayment(
            @PathVariable Long orderCode,
            Authentication authentication
    ) {
        UUID accountId = resolveAccountId(authentication);
        PayOSPaymentStatusResponse response = payOSPaymentService.cancelPayment(orderCode, accountId, "User requested cancellation");

        return ResponseEntity.ok(ApiResponseDto.<PayOSPaymentStatusResponse>builder()
                .status("success")
                .message("Payment cancelled successfully")
                .data(response)
                .build());
    }

    private UUID resolveAccountId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtUserDetails userDetails) {
            return userDetails.getUserId();
        }

        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
