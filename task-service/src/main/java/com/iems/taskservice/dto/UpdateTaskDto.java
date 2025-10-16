package com.iems.taskservice.dto;

import com.iems.taskservice.entity.enums.TaskPriority;
import com.iems.taskservice.entity.enums.TaskStatus;
import com.iems.taskservice.entity.enums.TaskType;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateTaskDto {
    private UUID projectId;

    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private UUID assignedTo;

    private TaskStatus status; // Using enum for type safety

    private TaskPriority priority; // Using enum for type safety

    private TaskType taskType; // Allow changing task type

    private LocalDate startDate;

    private LocalDate dueDate;

    @Size(max = 500, message = "Comment must not exceed 500 characters")
    private String comment; // Optional comment when updating status

    private UUID parentTaskId; // Allow reparenting or setting as subtask
}