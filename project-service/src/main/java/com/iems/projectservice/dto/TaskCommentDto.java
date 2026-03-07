package com.iems.projectservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCommentDto {
    private UUID id;
    private UUID taskId;
    private UUID authorId;
    private String authorName;
    private String authorAvatar;
    private String content;
    private UUID parentCommentId;
    private String parentAuthorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
