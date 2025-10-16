package com.iems.departmentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentResponseDto {
    private UUID id;
    private String departmentName;
    private String description;
    private UUID managerId;
    private String managerName;
    private long totalUsers;
}