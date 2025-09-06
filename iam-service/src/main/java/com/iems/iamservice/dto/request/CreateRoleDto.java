package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class CreateRoleDto {
    
    @NotBlank(message = "Role code cannot be blank")
    private String code;
    
    @NotBlank(message = "Role name cannot be blank")
    private String name;
    
    private String description;
    private Set<String> permissionCodes;
}


