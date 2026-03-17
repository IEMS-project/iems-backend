package com.iems.projectservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BurndownDataPointDto {
    private LocalDate date;
    private Integer idealRemaining;
    private Integer actualRemaining;
}
