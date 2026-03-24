package com.iems.documentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class DocumentActivityResponse {
    private UUID id;
    private String actionKey;
    private String message;
    private Map<String, Object> payload;
    private UUID actorUserId;
    private String actorName;
    private String actorEmail;
    private OffsetDateTime timestamp;
}
