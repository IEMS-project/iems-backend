package com.iems.documentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TrashItemResponse {
    private UUID id;
    private String name;
    private String itemType; // "FILE" or "FOLDER"
    private Long size;
    private String mimeType;
    private UUID parentId; // Original parent folder id for restoration
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
}
