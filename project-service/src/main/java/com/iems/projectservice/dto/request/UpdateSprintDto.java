package com.iems.projectservice.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateSprintDto {
    private String name;
    private String goal;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
