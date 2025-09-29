package com.iems.departmentservice.dto.response;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

// removed joinedAt/leftAt/isActive fields per new design
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DepartmentUserDto {
    private UUID id;
    private UUID departmentId;
    private UUID userId;
    
}
