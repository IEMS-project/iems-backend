package com.iems.documentservice.dto.request;

import com.iems.documentservice.entity.Permission;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePermissionRequest {
    @NotNull
    private Permission permission;
}



