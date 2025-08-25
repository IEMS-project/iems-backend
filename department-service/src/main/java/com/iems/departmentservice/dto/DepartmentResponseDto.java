package com.iems.departmentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentResponseDto {
    private UUID id;
    private String departmentName;
    private String description;
    private UUID managerId;
}