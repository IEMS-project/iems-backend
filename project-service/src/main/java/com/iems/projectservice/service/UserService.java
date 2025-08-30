package com.iems.projectservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final RestTemplate restTemplate;
    
    // This would typically be configured via application.properties
    private static final String USER_SERVICE_URL = "http://user-service";
    
    public UserDto getUserById(UUID userId) {
        try {
            // This would make a REST call to user-service
            // For now, returning mock data
            log.info("Fetching user info for userId: {}", userId);
            return new UserDto(userId, "User " + userId, "user" + userId + "@example.com", "ACTIVE");
        } catch (Exception e) {
            log.error("Error fetching user info for userId: {}", userId, e);
            throw new RuntimeException("Failed to fetch user information");
        }
    }
    
    public boolean isAdmin(UUID userId) {
        try {
            // This would check user roles from IAM service
            // For now, returning mock data
            log.info("Checking admin status for userId: {}", userId);
            return userId.toString().equals("00000000-0000-0000-0000-000000000001"); // Mock: specific UUID is admin
        } catch (Exception e) {
            log.error("Error checking admin status for userId: {}", userId, e);
            return false;
        }
    }
    
    public static class UserDto {
        private UUID id;
        private String name;
        private String email;
        private String status;
        
        public UserDto(UUID id, String name, String email, String status) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.status = status;
        }
        
        // Getters and setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
