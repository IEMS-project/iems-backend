package com.iems.documentservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateFolderRequest {
    @NotBlank
    private String name;

    private UUID parentId;
}



