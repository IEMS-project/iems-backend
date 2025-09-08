package com.iems.documentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class FolderResponse {
    private UUID id;
    private String name;
    private UUID parentId;
    private UUID ownerId;
    private OffsetDateTime createdAt;
}



