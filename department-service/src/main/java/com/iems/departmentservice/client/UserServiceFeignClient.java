package com.iems.departmentservice.client;

import com.iems.departmentservice.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "USER-SERVICE",
        configuration = FeignClientConfig.class
)
public interface UserServiceFeignClient {

    @GetMapping("/users/{userId}")
    ResponseEntity<Map<String, Object>> getUserById(@PathVariable("userId") UUID userId);
}