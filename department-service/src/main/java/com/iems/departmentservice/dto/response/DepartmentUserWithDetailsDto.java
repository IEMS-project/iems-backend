package com.iems.departmentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentUserWithDetailsDto {
    private UUID id;
    private UUID departmentId;
    private UUID userId;
    //private String role;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private boolean isActive;
    private UserDetailDto userDetails;
}
