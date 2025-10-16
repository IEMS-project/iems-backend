package com.iems.documentservice.dto.response;

import com.iems.documentservice.entity.enums.Permission;
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
    private Permission permission;
    private OffsetDateTime createdAt;
    private boolean favorite;
}



