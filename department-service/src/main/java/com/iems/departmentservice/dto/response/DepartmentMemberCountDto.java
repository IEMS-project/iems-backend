package com.iems.departmentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentMemberCountDto {
    private UUID id;
    private String departmentName;
    private long activeUsers;
}



