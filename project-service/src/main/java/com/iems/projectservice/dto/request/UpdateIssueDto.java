package com.iems.projectservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
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
    @JsonIgnore
    private boolean assigneeIdSet;
    private UUID sprintId;
    private UUID parentId;
    private Integer storyPoints;
    private Integer sortOrder;
    private LocalDate dueDate;

    @JsonSetter("assigneeId")
    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
        this.assigneeIdSet = true;
    }

    private java.util.List<UUID> labelIds;
    private java.util.List<AttachmentRequestDto> attachments;
}
