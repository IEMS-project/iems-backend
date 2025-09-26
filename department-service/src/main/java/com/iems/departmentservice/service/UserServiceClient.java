package com.iems.departmentservice.service;

import com.iems.departmentservice.dto.response.UserDetailDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceClient {
    
    @Autowired
    private RestTemplate restTemplate;
    
    private static final String USER_SERVICE_URL = "http://user-service/users";
    
    public List<UserDetailDto> getUsersByIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        
        try {
            // Call User Service to get all users
            HttpHeaders headers = buildAuthHeaders();
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                USER_SERVICE_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> allUsers = (List<Map<String, Object>>) response.getBody().get("data");
                
                // Filter users by the requested IDs
                return allUsers.stream()
                    .filter(user -> userIds.contains(UUID.fromString(user.get("id").toString())))
                    .map(this::convertToUserDetailDto)
                    .collect(Collectors.toList());
            }
            
            return List.of();
        } catch (Exception e) {
            // Log error and return empty list
            System.err.println("Error fetching users from User Service: " + e.getMessage());
            return List.of();
        }
    }
    
    public UserDetailDto getUserById(UUID userId) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                USER_SERVICE_URL + "/" + userId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userData = (Map<String, Object>) response.getBody().get("data");
                return convertToUserDetailDto(userData);
            }
            
            return null;
        } catch (Exception e) {
            System.err.println("Error fetching user from User Service: " + e.getMessage());
            return null;
        }
    }
    
    private UserDetailDto convertToUserDetailDto(Map<String, Object> userData) {
        UserDetailDto dto = new UserDetailDto();
        dto.setId(UUID.fromString(userData.get("id").toString()));
        dto.setFirstName((String) userData.get("firstName"));
        dto.setLastName((String) userData.get("lastName"));
        dto.setEmail((String) userData.get("email"));
        dto.setAddress((String) userData.get("address"));
        dto.setPhone((String) userData.get("phone"));
        dto.setDob((String) userData.get("dob"));
        dto.setGender((String) userData.get("gender"));
        dto.setPersonalID((String) userData.get("personalID"));
        dto.setImage((String) userData.get("image"));
        dto.setBankAccountNumber((String) userData.get("bankAccountNumber"));
        dto.setBankName((String) userData.get("bankName"));
        dto.setContractType((String) userData.get("contractType"));
        dto.setStartDate((String) userData.get("startDate"));
        return dto;
    }
    
    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        // Try to forward Authorization header from current request
        try {
            var requestAttributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
                String authHeader = servletAttrs.getRequest().getHeader("Authorization");
                if (authHeader != null && !authHeader.isBlank()) {
                    headers.add("Authorization", authHeader);
                }
            }
        } catch (Exception ignored) {}
        return headers;
    }
}

