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
public class AssignRoleRequestDto {

    @NotEmpty(message = "Role codes list cannot be empty")
    private Set<String> roleCodes;
}
