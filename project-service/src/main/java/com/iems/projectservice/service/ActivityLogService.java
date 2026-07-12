package com.iems.projectservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.AccountIdsDto;
import com.iems.projectservice.dto.response.ActivityLogResponseDto;
import com.iems.projectservice.dto.response.UserDetailDto;
import com.iems.projectservice.entity.ActivityLog;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.repository.ActivityLogRepository;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.iems.projectservice.dto.response.PagedResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final ProjectRepository projectRepository;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;

    /**
     * Returns log for activity log processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param issueId the issue id parameter
     * @param userId the user id parameter
     * @param action the action parameter
     * @param details the details parameter
     * @return the log result
     */
    public ActivityLog log(UUID projectId, UUID issueId, UUID userId, String action, String details) {
        ActivityLog entry = new ActivityLog();
        entry.setProjectId(projectId);
        entry.setIssueId(issueId);
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setDetails(details);
        return activityLogRepository.save(entry);
    }

    /**
     * Retrieves activity log information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param pageable the pageable parameter
     * @return the paginated result set
     */
    public PagedResponseDto<ActivityLogResponseDto> getProjectActivities(UUID projectId, Pageable pageable) {
        Page<ActivityLog> page = activityLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable);
        return toPagedResponse(page);
    }

    /**
     * Retrieves activity log information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param pageable the pageable parameter
     * @return the paginated result set
     */
    public PagedResponseDto<ActivityLogResponseDto> getMyRecentActivities(Pageable pageable) {
        UUID accountId = getUserIdFromRequest();
        List<UUID> projectIds = projectRepository.findIdsByOwnerOrMember(accountId);
        if (projectIds.isEmpty()) {
            return toPagedResponse(new PageImpl<>(List.of(), pageable, 0));
        }

        Page<ActivityLog> page = activityLogRepository.findByProjectIdInAndUserIdOrderByCreatedAtDesc(
                projectIds, accountId, pageable);
        return toPagedResponse(page);
    }

    /**
     * Retrieves activity log information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param issueId the issue id parameter
     * @param pageable the pageable parameter
     * @return the paginated result set
     */
    public PagedResponseDto<ActivityLogResponseDto> getIssueActivities(UUID issueId, Pageable pageable) {
        Page<ActivityLog> page = activityLogRepository.findByIssueIdOrderByCreatedAtDesc(issueId, pageable);
        return toPagedResponse(page);
    }

    /**
     * Returns to paged response for activity log processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param page the page parameter
     * @return the paginated result set
     */
    private PagedResponseDto<ActivityLogResponseDto> toPagedResponse(Page<ActivityLog> page) {
        return new PagedResponseDto<>(
                enrich(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    /**
     * Returns enrich for activity log processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param logs the logs parameter
     * @return the matching result collection
     */
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
        Set<UUID> projectIds = logs.stream()
                .map(ActivityLog::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> projectNames = projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));

        return logs.stream().map(l -> {
            UserDetailDto user = finalUserMap.get(l.getUserId());
            String name = user != null
                    ? (trim(user.getFirstName()) + " " + trim(user.getLastName())).trim()
                    : null;
            ActivityLogResponseDto dto = new ActivityLogResponseDto(
                    l.getId(),
                    l.getProjectId(),
                    l.getIssueId(),
                    l.getUserId(),
                    name,
                    user != null ? user.getImage() : null,
                    l.getAction(),
                    l.getDetails(),
                    l.getCreatedAt());
            dto.setProjectName(projectNames.get(l.getProjectId()));
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Retrieves activity log information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the get user id from request result
     */
    private UUID getUserIdFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    /**
     * Returns trim for activity log processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param s the s parameter
     * @return the trim result
     */
    private String trim(String s) {
        return s != null ? s.trim() : "";
    }
}
