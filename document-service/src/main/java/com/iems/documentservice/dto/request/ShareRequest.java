package com.iems.documentservice.dto.request;

import com.iems.documentservice.entity.enums.SharePermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ShareRequest {
    @NotEmpty
    private List<UUID> userIds;
    
    @NotNull
    private SharePermission permission;
}



