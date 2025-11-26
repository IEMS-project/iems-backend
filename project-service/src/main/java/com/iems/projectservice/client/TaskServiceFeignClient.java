package com.iems.projectservice.client;

import com.iems.projectservice.config.FeignClientConfig;
import com.iems.projectservice.dto.request.ProjectIdsDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.ProjectProgressDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "TASK-SERVICE",
        configuration = FeignClientConfig.class
)
public interface TaskServiceFeignClient {
    @PostMapping("/tasks/project-completions")
    ApiResponseDto<List<ProjectProgressDto>> getProjectCompletions(@RequestBody ProjectIdsDto projectIdsDto);

}
