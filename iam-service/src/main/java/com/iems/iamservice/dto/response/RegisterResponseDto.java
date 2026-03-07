package com.iems.iamservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for user registration response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponseDto {

    private UUID accountId;
    private UUID userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String message;
}
