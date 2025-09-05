package com.iems.documentservice.dto.request;

import com.iems.documentservice.entity.StoredFile;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePermissionRequest {
    @NotNull
    private StoredFile.Permission permission;

    // optional: owner performing action, could be from auth in real app
    @NotNull
    private Long ownerId;
}



