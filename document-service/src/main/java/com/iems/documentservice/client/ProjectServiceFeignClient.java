package com.iems.documentservice.client;

import com.iems.documentservice.config.FeignClientConfig;
import com.iems.documentservice.dto.request.UpdateProjectAvatarRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
        name = "PROJECT-SERVICE",
        configuration = FeignClientConfig.class
)
public interface ProjectServiceFeignClient {

    /**
     * Check if the current JWT user has a specific permission in the project.
     */
    @GetMapping("/projects/{projectId}/members/permissions/{permission}/check")
    ResponseEntity<Void> checkPermission(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("permission") String permission);

    /**
     * Check if the current user is a member of the project.
     */
    @GetMapping("/projects/{projectId}/members/check")
    ResponseEntity<Void> checkMembership(@PathVariable("projectId") UUID projectId);

    @PutMapping("/projects/{projectId}/avatar")
    ResponseEntity<Object> updateProjectAvatar(@PathVariable("projectId") UUID projectId,
                                               @RequestBody UpdateProjectAvatarRequest body);

}
