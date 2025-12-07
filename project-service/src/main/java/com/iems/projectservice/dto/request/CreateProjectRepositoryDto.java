package com.iems.projectservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRepositoryDto {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotBlank(message = "Repository name is required")
    private String name;

    @NotBlank(message = "Repository link is required")
    private String repoLink;
}
