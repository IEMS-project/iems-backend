package com.iems.projectservice.dto.request;

import com.iems.projectservice.entity.enums.StatusCategory;
import lombok.Data;

@Data
public class UpdateWorkflowStatusDto {
    private String name;
    private StatusCategory category;
    private Integer sortOrder;
    private String color;
}
