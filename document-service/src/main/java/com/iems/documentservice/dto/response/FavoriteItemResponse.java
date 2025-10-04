package com.iems.documentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class FavoriteItemResponse {
    private UUID id;           // favorite id
    private UUID targetId;
    private String name;
    private String targetType; // FILE or FOLDER (determined by checking existence)
}



