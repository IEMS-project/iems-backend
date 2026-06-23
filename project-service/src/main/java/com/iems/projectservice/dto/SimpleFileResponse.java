package com.iems.projectservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleFileResponse {
    private String id;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
}
