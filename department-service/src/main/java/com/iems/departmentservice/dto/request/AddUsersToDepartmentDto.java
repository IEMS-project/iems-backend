package com.iems.departmentservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddUsersToDepartmentDto {
    @NotEmpty(message = "User IDs are required")
    private List<UUID> userIds;
}



