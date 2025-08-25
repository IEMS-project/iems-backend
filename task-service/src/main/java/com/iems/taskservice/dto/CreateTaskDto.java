package com.iems.taskservice.dto;

import com.iems.taskservice.entity.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateTaskDto {
    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotNull(message = "Assigned to is required")
    private UUID assignedTo;

    @NotNull(message = "Priority is required")
    private TaskPriority priority; // Using enum for validation

    private LocalDate startDate;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;
}