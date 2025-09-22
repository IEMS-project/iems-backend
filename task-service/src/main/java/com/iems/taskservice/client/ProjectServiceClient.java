package com.iems.taskservice.client;

import com.iems.taskservice.dto.external.ProjectInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${services.project-service.url:http://project-service}")
    private String projectServiceUrl;
    
    public ProjectInfoDto getProjectById(UUID projectId) {
        try {
            log.info("Fetching project info for projectId: {}", projectId);
            String url = projectServiceUrl + "/api/projects/external/" + projectId;
            ProjectInfoDto project = restTemplate.getForObject(url, ProjectInfoDto.class);
            log.info("Successfully fetched project info: {}", project);
            return project;
        } catch (Exception e) {
            log.error("Error fetching project info for projectId: {}", projectId, e);
            throw new RuntimeException("Failed to fetch project information: " + e.getMessage());
        }
    }
    
    public boolean isProjectActive(UUID projectId) {
        try {
            ProjectInfoDto project = getProjectById(projectId);
            return project != null && project.getStatus() != null;
        } catch (Exception e) {
            log.error("Error checking project status for projectId: {}", projectId, e);
            return false;
        }
    }
}
