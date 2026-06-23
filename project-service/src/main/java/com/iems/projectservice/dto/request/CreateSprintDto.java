package com.iems.projectservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateSprintDto {
    @NotBlank
    private String name;
    private String goal;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
