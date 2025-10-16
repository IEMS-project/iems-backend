package com.iems.iamservice.dto.response;

import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
public class UserResponseDto {
    private UUID id;
    private UUID userId;
    private String username;
    private String email;
    private Boolean enabled;
    private Instant createdAt;
    private Set<String> roles;
    private Set<String> permissions;
}


