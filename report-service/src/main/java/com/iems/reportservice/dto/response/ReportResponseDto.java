package com.iems.reportservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportResponseDto {
    private UUID id;
    private String title;
    private String fileUrl;
    private UUID createdBy;
    private LocalDateTime createdAt;

    // Danh sách người nhận báo cáo
    private List<ReportReceiverResponse> receivers;
}
