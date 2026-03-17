package com.iems.projectservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.AccountIdsDto;
import com.iems.projectservice.dto.response.ActivityLogResponseDto;
import com.iems.projectservice.dto.response.UserDetailDto;
import com.iems.projectservice.entity.ActivityLog;
import com.iems.projectservice.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.iems.projectservice.dto.response.PagedResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;

    public ActivityLog log(UUID projectId, UUID issueId, UUID userId, String action, String details) {
        ActivityLog entry = new ActivityLog();
        entry.setProjectId(projectId);
        entry.setIssueId(issueId);
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setDetails(details);
        return activityLogRepository.save(entry);
    }

    public PagedResponseDto<ActivityLogResponseDto> getProjectActivities(UUID projectId, Pageable pageable) {
        Page<ActivityLog> page = activityLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable);
        return toPagedResponse(page);
    }

    public PagedResponseDto<ActivityLogResponseDto> getIssueActivities(UUID issueId, Pageable pageable) {
        Page<ActivityLog> page = activityLogRepository.findByIssueIdOrderByCreatedAtDesc(issueId, pageable);
        return toPagedResponse(page);
    }

    private PagedResponseDto<ActivityLogResponseDto> toPagedResponse(Page<ActivityLog> page) {
        return new PagedResponseDto<>(
                enrich(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    private List<ActivityLogResponseDto> enrich(List<ActivityLog> logs) {
        if (logs.isEmpty())
            return List.of();

        Set<UUID> userIds = logs.stream()
                .map(ActivityLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserDetailDto> userMap = new HashMap<>();
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient
                    .getUsersByAccountIds(new AccountIdsDto(userIds));
            if (response.getBody() != null && response.getBody().get("data") != null) {
                List<UserDetailDto> users = objectMapper.convertValue(
                        response.getBody().get("data"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, UserDetailDto.class));
                userMap = users.stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserDetailDto::getId, u -> u));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user details for activity log: {}", e.getMessage());
        }

        final Map<UUID, UserDetailDto> finalUserMap = userMap;
        return logs.stream().map(l -> {
            UserDetailDto user = finalUserMap.get(l.getUserId());
            String name = user != null
                    ? (trim(user.getFirstName()) + " " + trim(user.getLastName())).trim()
                    : null;
            return new ActivityLogResponseDto(
                    l.getId(),
                    l.getProjectId(),
                    l.getIssueId(),
                    l.getUserId(),
                    name,
                    user != null ? user.getImage() : null,
                    l.getAction(),
                    l.getDetails(),
                    l.getCreatedAt());
        }).collect(Collectors.toList());
    }

    private String trim(String s) {
        return s != null ? s.trim() : "";
    }
}
