package com.iems.documentservice.dto.response;

import com.iems.documentservice.entity.StoredFile;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class FileResponse {
    private Long id;
    private String name;
    private Long folderId;
    private Long ownerId;
    private String path;
    private Long size;
    private String type;
    private StoredFile.Permission permission;
    private OffsetDateTime createdAt;
    private String presignedUrl;
}



