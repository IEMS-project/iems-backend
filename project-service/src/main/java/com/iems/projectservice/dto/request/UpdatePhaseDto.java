package com.iems.projectservice.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePhaseDto {
    
    private String name;
    
    private String description;
    
    private String goal;

    private LocalDateTime startDate;

    private LocalDateTime endDate;
    
    private Integer sortOrder;
}
