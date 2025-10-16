package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignPermissionRequestDto {

    @NotEmpty(message = "Permission codes list cannot be empty")
    private Set<String> permissionCodes;
}
