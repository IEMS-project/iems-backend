package com.iems.projectservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SprintBurndownDto {
    private UUID sprintId;
    private String sprintName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalStoryPoints;
    private Integer currentRemaining;
    private List<BurndownDataPointDto> points;
}
