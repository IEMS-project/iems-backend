package com.iems.documentservice.client;

import com.iems.documentservice.config.FeignClientConfig;
import com.iems.documentservice.dto.request.UpdateAvatarRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PutMapping("/users/me/avatar")
    ResponseEntity<Map<String, Object>> updateMyAvatar(@RequestBody UpdateAvatarRequest body);
}
