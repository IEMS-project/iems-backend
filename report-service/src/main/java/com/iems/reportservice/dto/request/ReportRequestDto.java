package com.iems.reportservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReportRequestDto {
    private String title;
    private String fileUrl;
    private UUID createdBy;

    // Danh sách người nhận (userId hoặc roleId)
    private List<UUID> receiverIds;
}
