package com.iems.departmentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// removed joinedAt/leftAt/isActive fields per new design
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentUserWithDetailsDto {
    private UUID id;
    private UUID departmentId;
    private UUID userId;
    private UserDetailDto userDetails;
}
