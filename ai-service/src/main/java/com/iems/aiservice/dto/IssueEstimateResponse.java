package com.iems.aiservice.dto;

import java.util.List;
import java.util.Map;

public record IssueEstimateResponse(
        Integer suggestedStoryPoints,
        String suggestedAssigneeId,
        String suggestedAssigneeName,
        double confidence,
        List<Map<String, Object>> similarIssues,
        List<Map<String, Object>> workloadSummary,
        List<String> reasons) {
}
