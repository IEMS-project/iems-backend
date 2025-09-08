package com.iems.documentservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ShareRequest {
    @NotEmpty
    private List<UUID> userIds;
}



