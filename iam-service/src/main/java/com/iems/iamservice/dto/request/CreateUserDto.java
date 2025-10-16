package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class CreateUserDto {
    
    @NotNull(message = "User ID cannot be null")
    private UUID userId; // ID from user-service
    
    @NotBlank(message = "Username cannot be blank")
    private String username;
    
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email is not valid")
    private String email;
    
    @NotBlank(message = "Password cannot be blank")
    private String password;
    
    private Set<String> roleCodes;
}


