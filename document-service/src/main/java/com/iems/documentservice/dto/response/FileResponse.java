package com.iems.documentservice.dto.response;

import com.iems.documentservice.entity.enums.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {
    private UUID id;
    private String name;
    private UUID folderId;
    private UUID ownerId;
    private Long size;
    private String type;
    private Permission permission;
    private OffsetDateTime createdAt;
    private String presignedUrl;
    private boolean favorite;

    // Owner details
    private String ownerName;
    private String ownerEmail;
    private String ownerAvatar;

    // Breadcrumbs
    private List<BreadcrumbResponse> breadcrumbs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreadcrumbResponse {
        private UUID id;
        private String name;
    }
}
