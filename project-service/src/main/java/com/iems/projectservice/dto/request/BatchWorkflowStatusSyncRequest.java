package com.iems.projectservice.dto.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchWorkflowStatusSyncRequest {

    @Valid
    private List<WorkflowStatusSyncItemDto> statuses = new ArrayList<>();
}
