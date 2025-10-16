package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePermissionDto {
    
    @NotBlank(message = "Permission code cannot be blank")
    private String code;
    
    @NotBlank(message = "Permission name cannot be blank")
    private String name;
    
    private String description;
}


