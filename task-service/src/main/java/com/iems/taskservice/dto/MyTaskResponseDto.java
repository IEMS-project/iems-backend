package com.iems.taskservice.dto;

import com.iems.taskservice.entity.TaskAttachment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyTaskResponseDto {
    private UUID id;

    //Info project
    private UUID projectId;
    private String projectName; // Added for better UX
    private String title;
    private String description;

    //Info task
    private String status;
    private String priority;
    private String taskType;
    private UUID parentTaskId;
    private UUID phaseId;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<TaskAttachmentDto> attachments;

}
