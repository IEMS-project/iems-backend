package com.iems.projectservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateIssueDto {
    @NotNull
    private UUID issueTypeId;

    private UUID parentId;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be 255 characters or less")
    private String title;

    @Size(max = 10000, message = "Description must be 10000 characters or less")
    private String description;
    private UUID priorityId;
    private UUID assigneeId;
    private UUID sprintId;
    private UUID statusId;
    @PositiveOrZero(message = "Story points must be zero or greater")
    private Integer storyPoints;
    private LocalDate dueDate;
    private java.util.List<UUID> labelIds;
    private java.util.List<AttachmentRequestDto> attachments;
}
