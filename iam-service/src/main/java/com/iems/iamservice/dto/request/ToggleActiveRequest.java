package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ToggleActiveRequest {
    @NotNull
    private Boolean active;
}
