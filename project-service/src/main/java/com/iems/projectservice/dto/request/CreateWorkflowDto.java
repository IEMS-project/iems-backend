package com.iems.projectservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWorkflowDto {
    @NotBlank
    private String name;
    private String description;
    private Boolean isDefault;
}
