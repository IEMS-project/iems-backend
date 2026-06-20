package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.SubscriptionLimitSettingsRequest;
import com.iems.projectservice.dto.response.SubscriptionLimitSettingsResponse;
import com.iems.projectservice.entity.SubscriptionLimitSettings;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.repository.SubscriptionLimitSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionLimitSettingsServiceTest {

    @Mock
    private SubscriptionLimitSettingsRepository repository;

    private SubscriptionLimitSettingsService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionLimitSettingsService(repository);
    }

    @Test
    void listShouldSeedDefaultsAndMapResults() {
        when(repository.existsById("FREE")).thenReturn(false);
        when(repository.existsById("PREMIUM")).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of(
                SubscriptionLimitSettings.builder().planType("FREE").maxOwnedProjects(2).build(),
                SubscriptionLimitSettings.builder().planType("PREMIUM").maxOwnedProjects(10).build()
        ));

        List<SubscriptionLimitSettingsResponse> result = service.list();

        assertEquals(2, result.size());
        verify(repository).save(any(SubscriptionLimitSettings.class));
    }

    @Test
    void updateShouldPersistNormalizedPlanType() {
        SubscriptionLimitSettings settings = SubscriptionLimitSettings.builder().planType("FREE").maxOwnedProjects(2).build();
        when(repository.existsById("FREE")).thenReturn(true);
        when(repository.existsById("PREMIUM")).thenReturn(true);
        when(repository.findById("FREE")).thenReturn(Optional.of(settings));
        when(repository.save(settings)).thenReturn(settings);

        SubscriptionLimitSettingsRequest request = new SubscriptionLimitSettingsRequest();
        request.setMaxOwnedProjects(5);
        request.setMaxMembersPerProject(10);
        request.setMaxIssuesPerProject(20);
        request.setMaxSprintsPerProject(3);
        request.setMaxCustomRolesPerProject(4);
        request.setActivityLogDays(15);
        request.setCustomWorkflowEnabled(true);
        request.setBurndownEnabled(true);
        request.setIssueTypePriorityCustomizationEnabled(true);

        SubscriptionLimitSettingsResponse response = service.update("free", request);

        assertEquals(5, response.getMaxOwnedProjects());
        assertEquals(10, response.getMaxMembersPerProject());
    }

    @Test
    void getSettingsShouldRejectInvalidPlanType() {
        assertThrows(AppException.class, () -> service.getSettings("enterprise"));
    }
}