package com.iems.documentservice.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiIndexCommandRequest {
    private String projectId;
    private String documentId;
    private String operation;
    private String fileName;
    private String fileType;
    private String downloadUrl;
}
