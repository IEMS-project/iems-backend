package com.iems.taskservice.dto;

import lombok.Data;

@Data
public class TaskUpdateResultDto {
    private String oldStatus;
    private String newStatus;
    private TaskNestedResponseDto task;
}


