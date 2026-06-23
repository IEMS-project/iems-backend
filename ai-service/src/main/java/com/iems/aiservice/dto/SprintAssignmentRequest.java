package com.iems.aiservice.dto;

import java.util.List;

public record SprintAssignmentRequest(
        String sprintId,
        List<String> issueIds) {
}
