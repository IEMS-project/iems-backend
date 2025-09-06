package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for lock/unlock user request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockUserRequestDto {

    @NotNull(message = "Lock status cannot be null")
    private Boolean locked;

    private String reason; // Reason for lock/unlock
}
