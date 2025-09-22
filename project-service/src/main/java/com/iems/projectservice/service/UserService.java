package com.iems.projectservice.service;

import com.iems.projectservice.client.UserServiceClient;
import com.iems.projectservice.dto.external.UserInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserServiceClient userServiceClient;
    
    public UserInfoDto getUserById(UUID userId) {
        return userServiceClient.getUserById(userId);
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
    
    public boolean isUserActive(UUID userId) {
        return userServiceClient.isUserActive(userId);
    }
}
