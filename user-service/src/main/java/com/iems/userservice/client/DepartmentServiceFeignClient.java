package com.iems.userservice.client;

import com.iems.userservice.config.FeignClientConfig;
import com.iems.userservice.dto.request.AddUserToDepartmentDto;
import com.iems.userservice.dto.response.ApiResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(
        name = "department-service",
        configuration = FeignClientConfig.class
)
public interface DepartmentServiceFeignClient {

    @PostMapping("/departments/{departmentId}/users")
    ApiResponseDto<Object> addUserToDepartment(
            @PathVariable("departmentId") UUID departmentId,
            @RequestBody AddUserToDepartmentDto addUserDto
    );
}
