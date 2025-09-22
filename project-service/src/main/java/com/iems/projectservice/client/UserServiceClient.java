package com.iems.projectservice.client;

import com.iems.projectservice.dto.external.UserInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
            
            // Create headers and add JWT token if available
            HttpHeaders headers = new HttpHeaders();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() != null) {
                String token = authentication.getCredentials().toString();
                headers.set("Authorization", "Bearer " + token);
                log.debug("Forwarding JWT token for inter-service communication");
            }
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<UserInfoDto> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                UserInfoDto.class
            );
            
            UserInfoDto user = response.getBody();
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
