package com.iems.projectservice.dto;

import lombok.Data;

@Data
public class TaskBulkUpdateItemDto {
    private String oldStatus;
    private String newStatus;
    private TaskResponseDto task;
}


