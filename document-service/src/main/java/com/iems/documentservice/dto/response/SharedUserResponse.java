package com.iems.documentservice.dto.response;

import com.iems.documentservice.entity.enums.SharePermission;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class SharedUserResponse {
    private UUID shareId;
    private UUID userId;
    private String firstName;
    private String lastName;
    private String email;
    private String image;
    private SharePermission permission;
    private OffsetDateTime sharedAt;
}
