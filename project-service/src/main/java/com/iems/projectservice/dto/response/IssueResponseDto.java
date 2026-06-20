package com.iems.projectservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueResponseDto {
    private UUID id;
    private UUID projectId;
    private String issueKey;
    private UUID issueTypeId;
    private UUID parentId;
    private String title;
    private String description;
    private UUID statusId;
    private UUID priorityId;
    private UUID assigneeId;
    private UUID reporterId;
    private UUID sprintId;
    private Integer storyPoints;
    private Integer sortOrder;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Enriched user info — null if user ID is null or lookup fails */
    private UserInfoDto assignee;
    private UserInfoDto reporter;
    private java.util.Set<LabelDto> labels;

    private String statusName;
    private String statusCategory;
    private java.util.List<AttachmentResponseDto> attachments;
}
