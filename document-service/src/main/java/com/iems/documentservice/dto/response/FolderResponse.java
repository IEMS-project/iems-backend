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
public class FolderResponse {
    private UUID id;
    private String name;
    private UUID parentId;
    private UUID ownerId;
    private Permission permission;
    private OffsetDateTime createdAt;
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
