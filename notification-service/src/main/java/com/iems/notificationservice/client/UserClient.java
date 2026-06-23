package com.iems.notificationservice.client;

import com.iems.notificationservice.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "iam-service", url = "${app.iam-service-url:http://localhost:8081/iam-service}")
public interface UserClient {

    @GetMapping("/users/by-account/{accountId}/notification-preferences")
    ApiResponse<Map<String, Object>> getNotificationPreferences(@PathVariable("accountId") UUID accountId);
}
