package com.iems.documentservice.dto.request;

import com.iems.documentservice.entity.enums.SharePermission;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateSharePermissionRequest {
    @NotNull
    private UUID shareId;
    
    @NotNull
    private SharePermission permission;
}
