package com.iems.iamservice.dto.response;

import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Data
public class UserResponseDto {
    private Long id;
    private String username;
    private String email;
    private Boolean enabled;
    private Instant createdAt;
    private Set<String> roles;
}


