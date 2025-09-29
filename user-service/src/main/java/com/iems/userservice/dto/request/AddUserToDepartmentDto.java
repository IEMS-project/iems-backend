package com.iems.userservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddUserToDepartmentDto {
    @NotNull(message = "User ID is required")
    private UUID userId;
}
