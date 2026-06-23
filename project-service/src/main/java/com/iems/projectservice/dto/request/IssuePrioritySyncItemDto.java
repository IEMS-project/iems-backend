package com.iems.projectservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

@Data
public class IssuePrioritySyncItemDto {

    private UUID id;
    private String name;
    private String iconUrl;
    private String color;

    @JsonProperty("_removed")
    private Boolean removed;
}
