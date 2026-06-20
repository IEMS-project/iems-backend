package com.iems.projectservice.dto.request;

import lombok.Data;

@Data
public class AttachmentRequestDto {
    private String fileId;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
}
