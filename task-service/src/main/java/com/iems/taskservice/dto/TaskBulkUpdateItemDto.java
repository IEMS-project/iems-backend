package com.iems.taskservice.dto;

import lombok.Data;

@Data
public class TaskBulkUpdateItemDto {
    private String oldStatus;
    private String newStatus;
    private TaskResponseDto task;
}


