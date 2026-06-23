package com.iems.projectservice.dto.response;

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
public class CommentResponseDto {
    private UUID id;
    private UUID issueId;
    private UUID authorId;
    private String authorName;
    private String authorEmail;
    private String authorImage;
    private String content;
    private UUID parentCommentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
