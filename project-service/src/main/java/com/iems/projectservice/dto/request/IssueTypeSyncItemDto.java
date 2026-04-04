package com.iems.projectservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

@Data
public class IssueTypeSyncItemDto {

    private UUID id;
    private String name;
    private String description;
    private String iconUrl;

    @JsonProperty("_removed")
    private Boolean removed;
}
