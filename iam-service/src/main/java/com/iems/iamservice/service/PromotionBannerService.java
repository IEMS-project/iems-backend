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

    /**
     * Lists promotion banner information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<PromotionBannerResponse> listAll() {
        return promotionBannerRepository.findAllByOrderByPriorityDescCreatedAtDesc().stream().map(this::toResponse)
                .toList();
    }

    /**
     * Lists promotion banner information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param placement the placement parameter
     * @return the matching result collection
     */
    public List<PromotionBannerResponse> listActive(String placement) {
        return promotionBannerRepository.findActiveByPlacement(placement, Instant.now()).stream().map(this::toResponse)
                .toList();
    }

    /**
     * Creates promotion banner data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param request the request parameter
     * @return the create result
     */
    @Transactional
    public PromotionBannerResponse create(PromotionBannerRequest request) {
        PromotionBanner banner = new PromotionBanner();
        apply(banner, request);
        return toResponse(promotionBannerRepository.save(banner));
    }

    /**
     * Updates promotion banner data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param id the id parameter
     * @param request the request parameter
     * @return the update result
     */
    @Transactional
    public PromotionBannerResponse update(UUID id, PromotionBannerRequest request) {
        PromotionBanner banner = getEntity(id);
        apply(banner, request);
        return toResponse(promotionBannerRepository.save(banner));
    }

    /**
     * Returns set active for promotion banner processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param id the id parameter
     * @param active the active parameter
     * @return the set active result
     */
    @Transactional
    public PromotionBannerResponse setActive(UUID id, boolean active) {
        PromotionBanner banner = getEntity(id);
        banner.setActive(active);
        return toResponse(promotionBannerRepository.save(banner));
    }

    /**
     * Deletes promotion banner data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param id the id parameter
     */
    @Transactional
    public void delete(UUID id) {
        promotionBannerRepository.delete(getEntity(id));
    }

    /**
     * Retrieves promotion banner information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param id the id parameter
     * @return the get entity result
     */
    private PromotionBanner getEntity(UUID id) {
        return promotionBannerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));
    }

    /**
     * Applies promotion banner changes.
     *
     * @param banner the banner parameter
     * @param request the request parameter
     */
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

    /**
     * Returns to response for promotion banner processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param banner the banner parameter
     * @return the to response result
     */
    private PromotionBannerResponse toResponse(PromotionBanner banner) {
        return PromotionBannerResponse.builder()
                .id(banner.getId()).title(banner.getTitle()).description(banner.getDescription())
                .imageUrl(banner.getImageUrl()).ctaLabel(banner.getCtaLabel()).ctaUrl(banner.getCtaUrl())
                .placement(banner.getPlacement())
                .priority(banner.getPriority()).active(banner.getActive()).startsAt(banner.getStartsAt())
                .endsAt(banner.getEndsAt()).createdAt(banner.getCreatedAt()).updatedAt(banner.getUpdatedAt())
                .build();
    }
}
