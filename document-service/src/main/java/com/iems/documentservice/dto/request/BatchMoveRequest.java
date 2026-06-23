package com.iems.documentservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchMoveRequest {
    
    private List<UUID> fileIds;
    
    private List<UUID> folderIds;
    
    private UUID destinationFolderId; // null means move to root
}
