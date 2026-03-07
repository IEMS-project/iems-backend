package com.iems.projectservice.service.Impl;

import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.AccountIdsDto;
import com.iems.projectservice.dto.response.UserDetailDto;
import com.iems.projectservice.dto.request.UserIdsDto;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.security.JwtUserDetails;
import com.iems.projectservice.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements IUserService {

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    @Override
    public Optional<UserDetailDto> getUserById(UUID accountId) {
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUserByAccountId(accountId);

            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userData = (Map<String, Object>) response.getBody().get("data");
                return Optional.of(convertToUserDetailDto(userData));
            }

            return Optional.empty();
        } catch (Exception e) {
            // Log error and return empty
            System.err.println("Error fetching user by accountId " + accountId + " from User Service: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<UserDetailDto> getUsersByIds(UserIdsDto request) {
        try {
            if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
                return new ArrayList<>();
            }
            AccountIdsDto accountIdsDto = new AccountIdsDto();
            accountIdsDto.setAccountIds(request.getIds());
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUsersByAccountIds(accountIdsDto);
            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> usersData = (List<Map<String, Object>>) response.getBody().get("data");
                return usersData.stream()
                        .map(this::convertToUserDetailDto)
                        .toList();
            }
            return new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Error fetching users from User Service: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
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
    public UUID getUserIdFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtUserDetails)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        JwtUserDetails userDetails = (JwtUserDetails) principal;
        UUID userId = userDetails.getUserId();
        if (userId == null) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        return userId;
    }
}
