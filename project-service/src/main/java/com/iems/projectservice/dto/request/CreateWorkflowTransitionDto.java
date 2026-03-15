package com.iems.projectservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateWorkflowTransitionDto {
    @NotNull
    private UUID fromStatusId;
    @NotNull
    private UUID toStatusId;
    private String name;
}
