package com.iems.taskservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectProgressDto {
    private UUID projectId;
    List<PhaseProgressDto> phasesProgress;
}
