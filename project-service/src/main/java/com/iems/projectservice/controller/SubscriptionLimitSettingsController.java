package com.iems.projectservice.controller;

import com.iems.projectservice.dto.request.SubscriptionLimitSettingsRequest;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.SubscriptionLimitSettingsResponse;
import com.iems.projectservice.service.SubscriptionLimitSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Subscription Limits")
public class SubscriptionLimitSettingsController {

    private final SubscriptionLimitSettingsService settingsService;

    @GetMapping("/subscription-limits")
    public ResponseEntity<ApiResponseDto<List<SubscriptionLimitSettingsResponse>>> listPublic() {
        return ResponseEntity.ok(new ApiResponseDto<>(
                "success",
                "Subscription limits retrieved successfully",
                settingsService.list()
        ));
    }

    @GetMapping("/api/admin/subscription-limits")
    public ResponseEntity<ApiResponseDto<List<SubscriptionLimitSettingsResponse>>> listAdmin() {
        return listPublic();
    }

    @PutMapping("/api/admin/subscription-limits/{planType}")
    public ResponseEntity<ApiResponseDto<SubscriptionLimitSettingsResponse>> update(
            @PathVariable String planType,
            @Valid @RequestBody SubscriptionLimitSettingsRequest request
    ) {
        return ResponseEntity.ok(new ApiResponseDto<>(
                "success",
                "Subscription limits updated successfully",
                settingsService.update(planType, request)
        ));
    }
}
