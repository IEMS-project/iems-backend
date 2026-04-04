package com.iems.projectservice.dto.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchIssueTypeSyncRequest {

    @Valid
    private List<IssueTypeSyncItemDto> issueTypes = new ArrayList<>();
}
