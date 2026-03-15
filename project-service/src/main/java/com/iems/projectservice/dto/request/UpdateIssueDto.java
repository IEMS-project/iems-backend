package com.iems.projectservice.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateIssueDto {
    private String title;
    private String description;
    private UUID issueTypeId;
    private UUID statusId;
    private UUID priorityId;
    private UUID assigneeId;
    private UUID sprintId;
    private UUID parentId;
    private Integer storyPoints;
    private Integer sortOrder;
    private LocalDate dueDate;
}
