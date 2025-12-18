package com.iems.documentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class FavoriteItemResponse {
    private UUID id;           // favorite id
    private UUID targetId;
    private String name;
    private String targetType; // FILE or FOLDER (determined by checking existence)
    private Long size;         // file size (only for files)
    private String path;       // file path (only for files)
    private String mimeType;   // file mime type (only for files)
    private String permission; // PUBLIC or PRIVATE
    private OffsetDateTime createdAt;
    private UUID ownerId;
    private UUID parentId;     // parent folder id (for navigation)
}



