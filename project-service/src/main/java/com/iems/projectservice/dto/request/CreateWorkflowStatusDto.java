package com.iems.projectservice.dto.request;

import com.iems.projectservice.entity.enums.StatusCategory;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWorkflowStatusDto {
    @NotBlank
    private String name;
    private StatusCategory category;
    private Integer sortOrder;
    private String color;
}
