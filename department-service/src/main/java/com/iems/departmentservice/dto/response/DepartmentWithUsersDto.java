package com.iems.departmentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentWithUsersDto {
    private UUID id;
    private String departmentName;
    private String description;
    private UUID managerId;
    private String managerName;
    private LocalDateTime createdAt;
    private UUID createdBy;
    private LocalDateTime updatedAt;
    private UUID updatedBy;
    private List<DepartmentUserWithDetailsDto> users;
    private int totalUsers;
}

