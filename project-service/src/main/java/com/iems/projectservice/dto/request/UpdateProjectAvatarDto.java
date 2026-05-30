package com.iems.projectservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProjectAvatarDto {
    @NotBlank
    private String avatarUrl;
}
