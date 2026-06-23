package com.iems.projectservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateIssueDto {
    @Size(max = 255, message = "Title must be 255 characters or less")
    private String title;
    @Size(max = 10000, message = "Description must be 10000 characters or less")
    private String description;
    @JsonIgnore
    private boolean descriptionSet;
    private UUID issueTypeId;
    private UUID statusId;
    private UUID priorityId;
    @JsonIgnore
    private boolean priorityIdSet;
    private UUID assigneeId;
    @JsonIgnore
    private boolean assigneeIdSet;
    private UUID sprintId;
    @JsonIgnore
    private boolean sprintIdSet;
    private UUID parentId;
    @JsonIgnore
    private boolean parentIdSet;
    @PositiveOrZero(message = "Story points must be zero or greater")
    private Integer storyPoints;
    @JsonIgnore
    private boolean storyPointsSet;
    private Integer sortOrder;
    private LocalDate dueDate;
    @JsonIgnore
    private boolean dueDateSet;

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionSet = true;
    }

    @JsonSetter("priorityId")
    public void setPriorityId(UUID priorityId) {
        this.priorityId = priorityId;
        this.priorityIdSet = true;
    }

    @JsonSetter("assigneeId")
    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
        this.assigneeIdSet = true;
    }

    @JsonSetter("sprintId")
    public void setSprintId(UUID sprintId) {
        this.sprintId = sprintId;
        this.sprintIdSet = true;
    }

    @JsonSetter("parentId")
    public void setParentId(UUID parentId) {
        this.parentId = parentId;
        this.parentIdSet = true;
    }

    @JsonSetter("storyPoints")
    public void setStoryPoints(Integer storyPoints) {
        this.storyPoints = storyPoints;
        this.storyPointsSet = true;
    }

    @JsonSetter("dueDate")
    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        this.dueDateSet = true;
    }

    private java.util.List<UUID> labelIds;
    private java.util.List<AttachmentRequestDto> attachments;
}
