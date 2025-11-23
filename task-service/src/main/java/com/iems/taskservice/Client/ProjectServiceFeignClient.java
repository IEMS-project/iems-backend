package com.iems.taskservice.Client;

import com.iems.taskservice.config.FeignClientConfig;
import com.iems.taskservice.dto.ApiResponseDto;
import com.iems.taskservice.dto.ProjectIdsDto;
import com.iems.taskservice.dto.ProjectInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "PROJECT-SERVICE",
        configuration = FeignClientConfig.class
)
public interface ProjectServiceFeignClient {

    @GetMapping("/projects/{projectId}")
    ResponseEntity<Map<String, Object>> getProjectById(@PathVariable("projectId") UUID projectId);

    @PostMapping("/projects/by-ids")
    ResponseEntity<ApiResponseDto<List<ProjectInfoResponse>>> getProjectsByID(
            @RequestBody ProjectIdsDto request
    );
}


