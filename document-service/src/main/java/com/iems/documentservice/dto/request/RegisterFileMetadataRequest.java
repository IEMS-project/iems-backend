package com.iems.documentservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;

@Data
public class RegisterFileMetadataRequest {
    private UUID folderId;
    
    @NotBlank
    private String fileName;
    
    @NotBlank
    private String objectKey;
    
    private Long fileSize;
    
    private String fileType;
}
