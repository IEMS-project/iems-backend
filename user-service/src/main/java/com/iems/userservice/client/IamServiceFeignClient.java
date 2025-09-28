package com.iems.userservice.client;

import com.iems.userservice.config.FeignClientConfig;
import com.iems.userservice.dto.request.CreateAccountRequestDto;
import com.iems.userservice.dto.response.ApiResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "iam-service" ,
        configuration = FeignClientConfig.class

    )
public interface IamServiceFeignClient {
    
    @PostMapping("/api/accounts")
    ApiResponseDto<Object> createAccount(@RequestBody CreateAccountRequestDto request);
}
