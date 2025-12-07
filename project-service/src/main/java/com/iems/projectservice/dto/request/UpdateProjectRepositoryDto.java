package com.iems.projectservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProjectRepositoryDto {

    @NotBlank(message = "Repository name is required")
    private String name;

    @NotBlank(message = "Repository link is required")
    private String repoLink;
}
