package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.response.PromotionBannerResponse;
import com.iems.iamservice.service.PromotionBannerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
@Tag(name = "Promotions", description = "Client APIs for active promotions")
public class PromotionController {

    private final PromotionBannerService promotionBannerService;

    @GetMapping("/active")
    public ResponseEntity<ApiResponseDto<List<PromotionBannerResponse>>> active(
            @RequestParam(defaultValue = "SIDEBAR") String placement
    ) {
        return ResponseEntity.ok(ApiResponseDto.<List<PromotionBannerResponse>>builder()
                .status("success").message("Active promotions retrieved successfully")
                .data(promotionBannerService.listActive(placement)).build());
    }
}
