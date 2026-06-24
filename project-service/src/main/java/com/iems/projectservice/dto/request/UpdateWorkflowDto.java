package com.iems.projectservice.dto.request;

import lombok.Data;

@Data
public class UpdateWorkflowDto {
    private String name;
    private String description;
    private Boolean isDefault;
}
