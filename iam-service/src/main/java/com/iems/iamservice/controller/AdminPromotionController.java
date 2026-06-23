package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.PromotionBannerRequest;
import com.iems.iamservice.dto.request.ToggleActiveRequest;
import com.iems.iamservice.dto.response.PromotionBannerResponse;
import com.iems.iamservice.service.PromotionBannerService;
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
@RequestMapping("/api/admin/promotions")
@RequiredArgsConstructor
@Tag(name = "Admin Promotions", description = "Admin APIs for promote area management")
public class AdminPromotionController {

    private final PromotionBannerService promotionBannerService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<PromotionBannerResponse>>> list() {
        return ResponseEntity.ok(ApiResponseDto.<List<PromotionBannerResponse>>builder()
                .status("success").message("Promotions retrieved successfully")
                .data(promotionBannerService.listAll()).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<PromotionBannerResponse>> create(@Valid @RequestBody PromotionBannerRequest request) {
        return ResponseEntity.ok(ApiResponseDto.<PromotionBannerResponse>builder()
                .status("success").message("Promotion created successfully")
                .data(promotionBannerService.create(request)).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<PromotionBannerResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody PromotionBannerRequest request
    ) {
        return ResponseEntity.ok(ApiResponseDto.<PromotionBannerResponse>builder()
                .status("success").message("Promotion updated successfully")
                .data(promotionBannerService.update(id, request)).build());
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponseDto<PromotionBannerResponse>> setActive(
            @PathVariable UUID id,
            @Valid @RequestBody ToggleActiveRequest request
    ) {
        return ResponseEntity.ok(ApiResponseDto.<PromotionBannerResponse>builder()
                .status("success").message("Promotion status updated successfully")
                .data(promotionBannerService.setActive(id, request.getActive())).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable UUID id) {
        promotionBannerService.delete(id);
        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success").message("Promotion deleted successfully").build());
    }
}
