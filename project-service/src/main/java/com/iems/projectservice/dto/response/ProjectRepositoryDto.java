package com.iems.projectservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectRepositoryDto {
    private UUID id;
    private UUID projectId;
    private String name;
    private String repoLink;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
