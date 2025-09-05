package com.iems.documentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class FolderResponse {
    private Long id;
    private String name;
    private Long parentId;
    private Long ownerId;
    private OffsetDateTime createdAt;
}



