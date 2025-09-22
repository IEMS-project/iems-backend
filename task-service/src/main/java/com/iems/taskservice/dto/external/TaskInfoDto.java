package com.iems.taskservice.dto.external;

import com.iems.taskservice.entity.enums.TaskPriority;
import com.iems.taskservice.entity.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskInfoDto {
    private UUID id;
    private UUID projectId;
    private String title;
    private String description;
    private UUID assignedTo;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate startDate;
    private LocalDate dueDate;
}

