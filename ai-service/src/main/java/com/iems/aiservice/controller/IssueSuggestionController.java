package com.iems.aiservice.controller;

import com.iems.aiservice.dto.IssueEstimateRequest;
import com.iems.aiservice.dto.IssueEstimateResponse;
import com.iems.aiservice.dto.SprintAssignmentRequest;
import com.iems.aiservice.dto.SprintAssignmentResponse;
import com.iems.aiservice.service.IssueSuggestionService;
import com.iems.aiservice.service.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/ai/projects/{projectId}")
@RequiredArgsConstructor
public class IssueSuggestionController {

    private final IssueSuggestionService issueSuggestionService;
    private final JwtService jwtService;

    @PostMapping("/issue-suggestions/estimate")
    public ResponseEntity<IssueEstimateResponse> estimateIssue(
            @PathVariable UUID projectId,
            @Valid @RequestBody IssueEstimateRequest request,
            @RequestHeader("Authorization") String authorization) {
        validateAuthorization(authorization);
        return ResponseEntity.ok(issueSuggestionService.estimateIssue(projectId, request, authorization));
    }

    @PostMapping("/sprints/{sprintId}/sprint-suggestions/assign")
    public ResponseEntity<SprintAssignmentResponse> suggestSprintAssignments(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId,
            @RequestBody(required = false) SprintAssignmentRequest request,
            @RequestHeader("Authorization") String authorization) {
        validateAuthorization(authorization);
        return ResponseEntity.ok(issueSuggestionService.suggestSprintAssignments(projectId, sprintId, request, authorization));
    }

    @PostMapping("/sprint-suggestions/assign")
    public ResponseEntity<SprintAssignmentResponse> suggestSprintAssignments(
            @PathVariable UUID projectId,
            @RequestBody SprintAssignmentRequest request,
            @RequestHeader("Authorization") String authorization) {
        validateAuthorization(authorization);
        if (request == null || request.sprintId() == null || request.sprintId().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "sprintId is required");
        }
        return ResponseEntity.ok(issueSuggestionService.suggestSprintAssignments(
                projectId, UUID.fromString(request.sprintId()), request, authorization));
    }

    private void validateAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        try {
            jwtService.extractUserId(authorization.substring(7).trim());
        } catch (Exception ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid JWT token", ex);
        }
    }
}
