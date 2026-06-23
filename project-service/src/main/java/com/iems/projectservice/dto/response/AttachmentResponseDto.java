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
public class AttachmentResponseDto {
    private UUID id;
    private UUID issueId;
    private String fileId;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private UUID uploadedBy;
    private LocalDateTime uploadedAt;
}
