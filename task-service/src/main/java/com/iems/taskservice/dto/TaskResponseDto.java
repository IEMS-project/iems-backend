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
    private String assignedToName; // Added for better UX
    private UUID createdBy;
    private String createdByName; // Added for better UX
    private String status;
    private String priority;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID updatedBy;
    private String updatedByName; // Added for better UX
}