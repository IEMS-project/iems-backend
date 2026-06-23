package com.iems.projectservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchProjectMemberRequest {

    @NotEmpty(message = "Members list is required")
    @Valid
    private List<ProjectMemberDto> members;
}
