package com.iems.taskservice.client;

import com.iems.taskservice.dto.external.UserInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${services.user-service.url:http://user-service}")
    private String userServiceUrl;
    
    public UserInfoDto getUserById(UUID userId) {
        try {
            log.info("Fetching user info for userId: {}", userId);
            String url = userServiceUrl + "/users/external/" + userId;
            UserInfoDto user = restTemplate.getForObject(url, UserInfoDto.class);
            log.info("Successfully fetched user info: {}", user);
            return user;
        } catch (Exception e) {
            log.error("Error fetching user info for userId: {}", userId, e);
            throw new RuntimeException("Failed to fetch user information: " + e.getMessage());
        }
    }
    
    public boolean isUserActive(UUID userId) {
        try {
            UserInfoDto user = getUserById(userId);
            return user != null && "ACTIVE".equals(user.getStatus());
        } catch (Exception e) {
            log.error("Error checking user status for userId: {}", userId, e);
            return false;
        }
    }
}
