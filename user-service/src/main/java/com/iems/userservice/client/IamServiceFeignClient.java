package com.iems.userservice.client;

import com.iems.userservice.config.FeignClientConfig;
import com.iems.userservice.dto.request.CreateAccountRequestDto;
import com.iems.userservice.dto.response.ApiResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "iam-service" ,
        configuration = FeignClientConfig.class

    )
public interface IamServiceFeignClient {
    
    @PostMapping("/api/accounts")
    ApiResponseDto<Object> createAccount(@RequestBody CreateAccountRequestDto request);

    @GetMapping("/api/roles/users-by-roles")
    ApiResponseDto<List<UUID>> getUserIdsByRoleCodes(@RequestParam("roleCodes") List<String> roleCodes);
}
