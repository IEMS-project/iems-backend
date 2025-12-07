package com.iems.taskservice.dto;

import com.iems.taskservice.entity.enums.TaskPriority;
import com.iems.taskservice.entity.enums.TaskType;
import jakarta.validation.constraints.*;
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

    private String description;

    @NotNull(message = "Assigned to is required")
    private UUID assignedTo;

    @NotNull(message = "Priority is required")
    private TaskPriority priority; // Using enum for validation

    @NotNull(message = "Task type is required")
    private TaskType taskType;

    @FutureOrPresent(message = "Start date cannot be in the past")
    private LocalDate startDate;

    @FutureOrPresent(message = "Due date cannot be in the past")
    private LocalDate dueDate;

    // Optional parent task for subtask creation
    private UUID parentTaskId;

    // Optional phase this task belongs to
    private UUID phaseId;

    @AssertTrue(message = "Due date must not be earlier than start date")
    public boolean isDueAfterStart() {
        if (startDate == null || dueDate == null) return true;
        return !dueDate.isBefore(startDate);
    }
}