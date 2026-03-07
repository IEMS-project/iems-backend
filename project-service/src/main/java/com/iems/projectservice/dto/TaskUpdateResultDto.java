package com.iems.projectservice.dto;

import lombok.Data;

@Data
public class TaskUpdateResultDto {
    private String oldStatus;
    private String newStatus;
    private TaskNestedResponseDto task;
}


