package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.PromotionBannerRequest;
import com.iems.iamservice.dto.response.PromotionBannerResponse;
import com.iems.iamservice.entity.PromotionBanner;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
import com.iems.iamservice.repository.PromotionBannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionBannerService {

    private final PromotionBannerRepository promotionBannerRepository;

    public List<PromotionBannerResponse> listAll() {
        return promotionBannerRepository.findAllByOrderByPriorityDescCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public List<PromotionBannerResponse> listActive(String placement) {
        return promotionBannerRepository.findActiveByPlacement(placement, Instant.now()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PromotionBannerResponse create(PromotionBannerRequest request) {
        PromotionBanner banner = new PromotionBanner();
        apply(banner, request);
        return toResponse(promotionBannerRepository.save(banner));
    }

    @Transactional
    public PromotionBannerResponse update(UUID id, PromotionBannerRequest request) {
        PromotionBanner banner = getEntity(id);
        apply(banner, request);
        return toResponse(promotionBannerRepository.save(banner));
    }

    @Transactional
    public PromotionBannerResponse setActive(UUID id, boolean active) {
        PromotionBanner banner = getEntity(id);
        banner.setActive(active);
        return toResponse(promotionBannerRepository.save(banner));
    }

    @Transactional
    public void delete(UUID id) {
        promotionBannerRepository.delete(getEntity(id));
    }

    private PromotionBanner getEntity(UUID id) {
        return promotionBannerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));
    }

    private void apply(PromotionBanner banner, PromotionBannerRequest request) {
        banner.setTitle(request.getTitle().trim());
        banner.setDescription(request.getDescription());
        banner.setImageUrl(request.getImageUrl());
        banner.setCtaLabel(request.getCtaLabel());
        banner.setCtaUrl(request.getCtaUrl());
        banner.setPlacement(request.getPlacement().trim().toUpperCase());
        banner.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        banner.setActive(request.getActive() == null || request.getActive());
        banner.setStartsAt(request.getStartsAt());
        banner.setEndsAt(request.getEndsAt());
    }

    private PromotionBannerResponse toResponse(PromotionBanner banner) {
        return PromotionBannerResponse.builder()
                .id(banner.getId()).title(banner.getTitle()).description(banner.getDescription())
            .imageUrl(banner.getImageUrl()).ctaLabel(banner.getCtaLabel()).ctaUrl(banner.getCtaUrl()).placement(banner.getPlacement())
                .priority(banner.getPriority()).active(banner.getActive()).startsAt(banner.getStartsAt())
                .endsAt(banner.getEndsAt()).createdAt(banner.getCreatedAt()).updatedAt(banner.getUpdatedAt())
                .build();
    }
}
