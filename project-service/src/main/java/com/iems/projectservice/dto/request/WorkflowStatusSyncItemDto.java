package com.iems.projectservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iems.projectservice.entity.enums.StatusCategory;
import lombok.Data;

import java.util.UUID;

@Data
public class WorkflowStatusSyncItemDto {

    private UUID id;
    private String name;
    private String color;
    private StatusCategory category;

    @JsonProperty("_removed")
    private Boolean removed;
}
