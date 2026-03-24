package com.iems.documentservice.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDocumentResponse {

    private UUID id;
    private UUID projectId;
    private UUID fileId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private UUID uploadedBy;
    private OffsetDateTime createdAt;
    private String downloadUrl;
    private Boolean isFolder;
    private UUID parentId;
}
