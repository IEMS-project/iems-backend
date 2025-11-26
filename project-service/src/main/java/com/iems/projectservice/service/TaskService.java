package com.iems.projectservice.service;

import com.iems.projectservice.client.TaskServiceFeignClient;
import com.iems.projectservice.dto.request.ProjectIdsDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.ProjectProgressDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskService {
    @Autowired
    private TaskServiceFeignClient taskServiceFeignClient;

    public List<ProjectProgressDto> getProjectsProgress(List<UUID> projectIds) {
        ProjectIdsDto dto = new ProjectIdsDto(new HashSet<>(projectIds));

        ApiResponseDto<List<ProjectProgressDto>> response = taskServiceFeignClient.getProjectCompletions(dto);

        if ("success".equalsIgnoreCase(response.getStatus())) {
            return response.getData();
        } else {
            throw new RuntimeException("Failed to get project progress from Task Service");
        }
    }
}
