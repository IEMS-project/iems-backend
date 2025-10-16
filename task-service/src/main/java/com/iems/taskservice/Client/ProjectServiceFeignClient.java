package com.iems.taskservice.Client;

import com.iems.taskservice.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "PROJECT-SERVICE",
        configuration = FeignClientConfig.class
)
public interface ProjectServiceFeignClient {

    @GetMapping("/projects/{projectId}")
    ResponseEntity<Map<String, Object>> getProjectById(@PathVariable("projectId") UUID projectId);
}


