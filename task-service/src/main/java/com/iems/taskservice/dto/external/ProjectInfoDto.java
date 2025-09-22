package com.iems.taskservice.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectInfoDto {
    private UUID id;
    private String name;
    private String description;
    private String status;
    private UUID managerId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}


