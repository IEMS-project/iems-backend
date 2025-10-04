package com.iems.documentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class SearchResultItem {
    private UUID id;
    private String name;
    private String itemType; // FILE or FOLDER
    private UUID parentId;   // for folder: parent, for file: folderId
    private Long size;       // files only
    private String mimeType; // files only
    private OffsetDateTime createdAt;
}



