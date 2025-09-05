package com.iems.documentservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateFolderRequest {
    @NotBlank
    private String name;

    private Long parentId;

    @NotNull
    private Long ownerId;
}



