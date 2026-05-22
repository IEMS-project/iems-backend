package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.response.PaymentTransactionResponse;
import com.iems.iamservice.dto.response.PayOSPaymentStatusResponse;
import com.iems.iamservice.entity.enums.PaymentStatus;
import com.iems.iamservice.service.PayOSPaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@Tag(name = "Admin Payments", description = "Admin APIs for payment history")
public class AdminPaymentController {

    private final PayOSPaymentService payOSPaymentService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<Page<PaymentTransactionResponse>>> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String planId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponseDto.<Page<PaymentTransactionResponse>>builder()
                .status("success").message("Payments retrieved successfully")
                .data(payOSPaymentService.searchPayments(status, accountId, planId, from, to, keyword, pageable)).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<PaymentTransactionResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponseDto.<PaymentTransactionResponse>builder()
                .status("success").message("Payment retrieved successfully")
                .data(payOSPaymentService.getPayment(id)).build());
    }

    @PostMapping("/{orderCode}/sync")
    public ResponseEntity<ApiResponseDto<PayOSPaymentStatusResponse>> sync(@PathVariable Long orderCode) {
        return ResponseEntity.ok(ApiResponseDto.<PayOSPaymentStatusResponse>builder()
                .status("success").message("Payment synced successfully")
                .data(payOSPaymentService.syncPaymentStatusForAdmin(orderCode)).build());
    }

    @PostMapping("/{orderCode}/cancel")
    public ResponseEntity<ApiResponseDto<PayOSPaymentStatusResponse>> cancel(@PathVariable Long orderCode) {
        return ResponseEntity.ok(ApiResponseDto.<PayOSPaymentStatusResponse>builder()
                .status("success").message("Payment cancelled successfully")
                .data(payOSPaymentService.cancelPaymentForAdmin(orderCode, "Admin requested cancellation")).build());
    }
}
