package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.PromotionBannerRequest;
import com.iems.iamservice.dto.response.PromotionBannerResponse;
import com.iems.iamservice.entity.PromotionBanner;
import com.iems.iamservice.repository.PromotionBannerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionBannerServiceTest {

    @Mock
    private PromotionBannerRepository promotionBannerRepository;

    private PromotionBannerService service;

    @BeforeEach
    void setUp() {
        service = new PromotionBannerService(promotionBannerRepository);
    }

    @Test
    void createShouldPersistBanner() {
        PromotionBannerRequest request = bannerRequest();
        PromotionBanner saved = PromotionBanner.builder().id(UUID.randomUUID()).title("Summer").active(true).build();
        when(promotionBannerRepository.save(any(PromotionBanner.class))).thenReturn(saved);

        PromotionBannerResponse response = service.create(request);

        assertEquals("Summer", response.getTitle());
    }

    @Test
    void setActiveShouldToggleBanner() {
        UUID id = UUID.randomUUID();
        PromotionBanner banner = PromotionBanner.builder().id(id).title("Summer").active(false).build();
        when(promotionBannerRepository.findById(id)).thenReturn(java.util.Optional.of(banner));
        when(promotionBannerRepository.save(banner)).thenReturn(banner);

        PromotionBannerResponse response = service.setActive(id, true);

        assertTrue(response.getActive());
        verify(promotionBannerRepository).save(banner);
    }

    @Test
    void listActiveShouldUseRepositoryFilter() {
        PromotionBanner banner = PromotionBanner.builder().id(UUID.randomUUID()).title("Summer").active(true).startsAt(Instant.now().minusSeconds(60)).build();
        when(promotionBannerRepository.findActiveByPlacement(org.mockito.ArgumentMatchers.eq("HOME"), any(Instant.class))).thenReturn(List.of(banner));

        List<PromotionBannerResponse> result = service.listActive("HOME");

        assertEquals(1, result.size());
        assertFalse(result.get(0).getTitle().isBlank());
    }

    private PromotionBannerRequest bannerRequest() {
        PromotionBannerRequest request = new PromotionBannerRequest();
        request.setTitle("Summer");
        request.setDescription("Sale");
        request.setImageUrl("https://example.com/banner.png");
        request.setCtaLabel("Buy now");
        request.setCtaUrl("https://example.com");
        request.setPlacement("HOME");
        request.setPriority(10);
        request.setActive(true);
        request.setStartsAt(Instant.now());
        return request;
    }
}