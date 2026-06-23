package com.iems.documentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeleteResponse {
    
    private int totalRequested;
    private int successCount;
    private int failureCount;
    private List<UUID> successfulFileIds;
    private List<UUID> successfulFolderIds;
    private List<FailedItem> failedItems;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedItem {
        private UUID id;
        private String type; // "file" or "folder"
        private String reason;
    }
}
