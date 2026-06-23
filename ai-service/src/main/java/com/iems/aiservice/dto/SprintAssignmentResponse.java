package com.iems.aiservice.dto;

import java.util.List;
import java.util.Map;

public record SprintAssignmentResponse(
        List<Map<String, Object>> assignments,
        List<Map<String, Object>> workloadSummary,
        List<String> reasons) {
}
