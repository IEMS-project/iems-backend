package com.iems.chatservice.service.Impl;

import com.iems.chatservice.client.UserServiceFeignClient;
import com.iems.chatservice.dto.UserDetailDto;
import com.iems.chatservice.security.JwtUserDetails;
import com.iems.chatservice.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements IUserService {

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    @Override
    public UUID getUserIdFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();
        return userId;
    }

    @Override
    public Optional<UserDetailDto> getUserById(UUID userId) {
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUserById(userId);

            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userData = (Map<String, Object>) response.getBody().get("data");
                return Optional.of(convertToUserDetailDto(userData));
            }

            return Optional.empty();
        } catch (Exception e) {
            System.err.println("Error fetching user " + userId + " from User Service: " + e.getMessage());
            return Optional.empty();
        }
    }
    public UserDetailDto convertToUserDetailDto(Map<String, Object> userData) {
        UserDetailDto dto = new UserDetailDto();
        dto.setId(UUID.fromString(userData.get("id").toString()));
        dto.setFirstName((String) userData.get("firstName"));
        dto.setLastName((String) userData.get("lastName"));
        dto.setEmail((String) userData.get("email"));
        dto.setAddress((String) userData.get("address"));
        dto.setPhone((String) userData.get("phone"));

        // Handle Date objects - convert to string
        Object dob = userData.get("dob");
        dto.setDob(dob != null ? dob.toString() : null);

        // Handle enum objects - convert to string
        Object gender = userData.get("gender");
        dto.setGender(gender != null ? gender.toString() : null);

        dto.setPersonalID((String) userData.get("personalID"));
        dto.setImage((String) userData.get("image"));
        dto.setBankAccountNumber((String) userData.get("bankAccountNumber"));
        dto.setBankName((String) userData.get("bankName"));

        // Handle enum objects - convert to string
        Object contractType = userData.get("contractType");
        dto.setContractType(contractType != null ? contractType.toString() : null);

        // Handle Date objects - convert to string
        Object startDate = userData.get("startDate");
        dto.setStartDate(startDate != null ? startDate.toString() : null);

        dto.setRole((String) userData.get("role"));
        return dto;
    }

    @Override
    public String resolveUserName(String userId) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(userId);
            var resp = userServiceFeignClient.getUserById(uuid);
            var body = resp.getBody();
            if (body != null && body.containsKey("data")) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> data = (java.util.Map<String, Object>) body.get("data");
                String firstName = data.getOrDefault("firstName", "").toString();
                String lastName = data.getOrDefault("lastName", "").toString();
                String email = data.getOrDefault("email", "").toString();
                String full = (firstName + " " + lastName).trim();
                return full.isBlank() ? (email.isBlank() ? userId : email) : full;
            }
        } catch (Exception e) {
            // Log the error for debugging
            System.err.println("Error resolving user name for " + userId + ": " + e.getMessage());
        }
        // Return a more user-friendly fallback instead of raw userId
        return "Người dùng";
    }
}
