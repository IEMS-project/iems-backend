package com.iems.reportservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportReceiverResponse {
    private UUID id;
    private UUID receiverId;
    private boolean isRead;
    private LocalDateTime readAt;
}
