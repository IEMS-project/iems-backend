package com.iems.documentservice.client;

import com.iems.documentservice.config.FeignClientConfig;
import com.iems.documentservice.dto.request.AccountIdsDto;
import com.iems.documentservice.dto.request.UpdateAvatarRequest;
import com.iems.documentservice.dto.request.UserIdsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "IAM-SERVICE",
        configuration = FeignClientConfig.class
)
public interface UserServiceFeignClient {

    @GetMapping("/users/{userId}")
    ResponseEntity<Map<String, Object>> getUserById(@PathVariable("userId") UUID userId);

    @PostMapping("/users/by-ids")
    ResponseEntity<Map<String, Object>> getUsersByID(
            @RequestBody UserIdsDto request
    );

    @PostMapping("/users/by-account-ids")
    ResponseEntity<Map<String, Object>> getUsersByAccountIds(
            @RequestBody AccountIdsDto request
    );

    @GetMapping("/users/by-account/{accountId}")
    ResponseEntity<Map<String, Object>> getUserByAccountId(@PathVariable("accountId") UUID accountId);

    @PutMapping("/users/me/avatar")
    ResponseEntity<Map<String, Object>> updateMyAvatar(@RequestBody UpdateAvatarRequest body);
}
