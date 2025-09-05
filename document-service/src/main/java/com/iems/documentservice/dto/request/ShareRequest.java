package com.iems.documentservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ShareRequest {
    @NotNull
    private Long ownerId;

    @NotEmpty
    private List<Long> userIds;
}



