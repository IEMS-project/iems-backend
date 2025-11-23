package com.iems.chatservice.client;

import com.iems.chatservice.config.FeignClientConfig;
import com.iems.chatservice.dto.UserIdsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "USER-SERVICE",
        configuration = FeignClientConfig.class
)
public interface UserServiceFeignClient {

    @GetMapping("/users/{userId}")
    ResponseEntity<Map<String, Object>> getUserById(@PathVariable("userId") UUID userId);

    @PostMapping("/users/by-ids")
    ResponseEntity<Map<String, Object>> getUsersByID(
            @RequestBody UserIdsDto request
    );
}