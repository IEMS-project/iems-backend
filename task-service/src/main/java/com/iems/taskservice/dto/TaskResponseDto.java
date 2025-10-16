package com.iems.taskservice.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TaskResponseDto {
    private UUID id;
    private UUID projectId;
    private String projectName; // Added for better UX
    private String title;
    private String description;

    private UUID assignedTo;
    private String assignedToName;
    private String assignedToEmail;
    private String assignedToImage;
    // Added for better UX
    private UUID createdBy;
    private String createdByName;
    private String createdByEmail;
    private String createdByImage;

    private UUID updatedBy;
    private String updatedByName;
    private String updatedByEmail;
    private String updatedByImage;
    // Added for better UX
    private String status;
    private String priority;
    private String taskType;
    private UUID parentTaskId;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


    // Added for better UX
}