package com.iems.projectservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRoleDto {
    @NotBlank
    private String name;
    private String description;
    private Boolean isDefault;
}
