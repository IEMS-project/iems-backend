package com.iems.userservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateAccountRequestDto {
    private UUID userId;
    private String username;
    private String email;
    private String password;
    private Set<String> roleCodes;
}
