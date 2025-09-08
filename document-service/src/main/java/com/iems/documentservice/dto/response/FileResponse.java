package com.iems.documentservice.dto.response;

import com.iems.documentservice.entity.Permission;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class FileResponse {
    private UUID id;
    private String name;
    private UUID folderId;
    private UUID ownerId;
    private String path;
    private Long size;
    private String type;
    private Permission permission;
    private OffsetDateTime createdAt;
    private String presignedUrl;
}



