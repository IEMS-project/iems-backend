package com.iems.projectservice.dto;

import com.iems.projectservice.entity.enums.TaskPriority;
import com.iems.projectservice.entity.enums.TaskStatus;
import com.iems.projectservice.entity.enums.TaskType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateTaskDto {
    private UUID projectId;

    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String description;

    private UUID assignedTo;

    private TaskStatus status; // Using enum for type safety

    private TaskPriority priority; // Using enum for type safety

    private TaskType taskType; // Allow changing task type

    @FutureOrPresent(message = "Start date cannot be in the past")
    private LocalDate startDate;

    @FutureOrPresent(message = "Due date cannot be in the past")
    private LocalDate dueDate;

    @Size(max = 500, message = "Comment must not exceed 500 characters")
    private String comment; // Optional comment when updating status

    private UUID parentTaskId; // Allow reparenting or setting as subtask

    private UUID phaseId; // Allow changing phase assignment

    @AssertTrue(message = "Due date must not be earlier than start date")
    public boolean isDueAfterStart() {
        if (startDate == null || dueDate == null) return true;
        return !dueDate.isBefore(startDate);
    }
}