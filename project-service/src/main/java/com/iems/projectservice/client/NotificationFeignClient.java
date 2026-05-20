package com.iems.projectservice.client;

import com.iems.projectservice.dto.request.CreateNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

@FeignClient(name = "notification-service", url = "${notification.service.url:http://localhost:8090}")
public interface NotificationFeignClient {

    @PostMapping("/notifications/internal")
    Map<String, Object> sendNotification(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody CreateNotificationRequest request);

    @PostMapping("/notifications/internal/batch")
    Map<String, Object> sendNotifications(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody List<CreateNotificationRequest> requests);
}
