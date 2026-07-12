package com.iems.aiservice.service;

import com.iems.aiservice.dto.IssueEstimateRequest;
import com.iems.aiservice.dto.IssueEstimateResponse;
import com.iems.aiservice.dto.SprintAssignmentRequest;
import com.iems.aiservice.dto.SprintAssignmentResponse;
import com.iems.aiservice.entity.IssueSuggestionVector;
import com.iems.aiservice.repository.IssueSuggestionVectorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IssueSuggestionService {

    private static final List<Integer> FIBONACCI_POINTS = List.of(0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89);
    private static final Set<String> STOP_WORDS = Set.of(
            "title", "description", "type", "priority", "labels",
            "task", "issue", "cong", "viec", "cho", "voi", "cua", "cac", "the", "and", "for", "from", "this",
            "that", "moi", "mot", "nhung", "dang", "can", "them", "sua", "tao", "cap", "nhat");
    private static final Map<String, String> DOMAIN_SYNONYMS = Map.ofEntries(
            Map.entry("login", "auth"),
            Map.entry("signin", "auth"),
            Map.entry("auth", "auth"),
            Map.entry("authentication", "auth"),
            Map.entry("oauth", "auth"),
            Map.entry("jwt", "auth"),
            Map.entry("nhap", "auth"),
            Map.entry("payment", "payment"),
            Map.entry("checkout", "payment"),
            Map.entry("invoice", "payment"),
            Map.entry("thanh", "payment"),
            Map.entry("toan", "payment"),
            Map.entry("profile", "user"),
            Map.entry("account", "user"),
            Map.entry("member", "user"),
            Map.entry("nguoi", "user"),
            Map.entry("dung", "user"),
            Map.entry("notification", "notify"),
            Map.entry("notify", "notify"),
            Map.entry("email", "notify"),
            Map.entry("mail", "notify"),
            Map.entry("dashboard", "report"),
            Map.entry("report", "report"),
            Map.entry("chart", "report"),
            Map.entry("cao", "report"),
            Map.entry("bug", "defect"),
            Map.entry("error", "defect"),
            Map.entry("exception", "defect"),
            Map.entry("loi", "defect"),
            Map.entry("ui", "frontend"),
            Map.entry("ux", "frontend"),
            Map.entry("frontend", "frontend"),
            Map.entry("giao", "frontend"),
            Map.entry("dien", "frontend"),
            Map.entry("api", "backend"),
            Map.entry("endpoint", "backend"),
            Map.entry("backend", "backend"));

    private final RestClient projectClient;
    private final OpenRouterEmbeddingService embeddingService;
    private final IssueSuggestionVectorRepository vectorRepository;

    /**
     * Creates a new issue suggestion service with project API and vector indexing dependencies.
     *
     * @param projectBaseUrl the project service base URL
     * @param embeddingService the embedding service used to compare issue content
     * @param vectorRepository the repository used to cache issue suggestion vectors
     */
    public IssueSuggestionService(
            @Value("${ai.agent.project-base-url:http://localhost:8080/project-service}") String projectBaseUrl,
            OpenRouterEmbeddingService embeddingService,
            IssueSuggestionVectorRepository vectorRepository) {
        this.projectClient = RestClient.builder().baseUrl(projectBaseUrl).build();
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
    }

    /**
     * Estimates issue suggestion data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @return the estimate issue result
     */
    public IssueEstimateResponse estimateIssue(UUID projectId, IssueEstimateRequest request, String authorization) {
        List<Map<String, Object>> issues = fetchList("/projects/" + projectId + "/issues", authorization);
        List<Map<String, Object>> members = fetchList("/projects/" + projectId + "/members", authorization);
        List<Map<String, Object>> statuses = fetchWorkflowStatuses(projectId, authorization);
        Map<String, String> issueTypeNames = nameById(fetchList("/projects/" + projectId + "/issue-types", authorization));
        Map<String, String> priorityNames = nameById(fetchList("/projects/" + projectId + "/issue-priorities", authorization));

        Set<String> doneStatusIds = statuses.stream()
                .filter(status -> normalize(stringValue(status.get("category"))).contains("done"))
                .map(status -> stringValue(status.get("id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        String requestText = issueText(
                request.title(),
                request.description(),
                stringValue(request.issueTypeId()),
                firstNonBlank(request.issueTypeName(), issueTypeNames.get(stringValue(request.issueTypeId()))),
                stringValue(request.priorityId()),
                firstNonBlank(request.priorityName(), priorityNames.get(stringValue(request.priorityId()))),
                null);
        List<Double> requestEmbedding = safeEmbed(requestText);
        List<Map<String, Object>> similarIssues = rankSimilarIssues(
                projectId.toString(),
                requestText,
                requestEmbedding,
                issues,
                doneStatusIds,
                issueTypeNames,
                priorityNames)
                .stream()
                .limit(5)
                .map(match -> compactIssue(match.issue(), match.score()))
                .toList();

        Integer point = suggestStoryPoints(similarIssues);
        List<Map<String, Object>> workload = buildWorkloadSummary(members, issues, doneStatusIds);
        Map<String, Object> assignee = chooseAssignee(workload, similarIssues, point);

        List<String> reasons = new ArrayList<>();
        if (!similarIssues.isEmpty()) {
            reasons.add("Đề xuất " + point + " pts vì " + similarIssues.size()
                    + " task Done tương đồng có median gần nhất là " + point + " pts; nổi bật: "
                    + topSimilarIssueReason(similarIssues) + ".");
        } else {
            reasons.add("Đề xuất " + point
                    + " pts bằng fallback heuristic vì project chưa đủ lịch sử task Done tương đồng.");
        }
        if (assignee != null) {
            int similarCount = intValue(assignee.get("similarIssueCount"), 0);
            int samePointCount = intValue(assignee.get("samePointHistoryCount"), 0);
            int openStoryPoints = intValue(assignee.get("openStoryPoints"), 0);
            int openIssueCount = intValue(assignee.get("openIssueCount"), 0);
            int completedIssueCount = intValue(assignee.get("completedIssueCount"), 0);
            String assigneeName = stringValue(assignee.get("name"));
            if (similarCount > 0 || samePointCount > 0) {
                reasons.add("Gợi ý " + assigneeName + " vì đang có " + openStoryPoints + " pts/"
                        + openIssueCount + " task chưa Done, từng làm " + similarCount
                        + " task tương đồng, " + samePointCount + " task cùng mức " + point
                        + " pts và đã hoàn thành " + completedIssueCount + " task.");
            } else {
                reasons.add("Gợi ý " + assigneeName + " vì workload thấp nhất: "
                        + openStoryPoints + " pts/" + openIssueCount
                        + " task chưa Done; chưa có lịch sử task tương đồng đủ mạnh.");
            }
        }

        return new IssueEstimateResponse(
                point,
                assignee == null ? null : stringValue(assignee.get("memberId")),
                assignee == null ? null : stringValue(assignee.get("name")),
                confidenceFor(similarIssues),
                similarIssues,
                workload,
                reasons);
    }

    /**
     * Builds issue suggestion suggestions.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param sprintId the sprint id parameter
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @return the suggest sprint assignments result
     */
    public SprintAssignmentResponse suggestSprintAssignments(UUID projectId, UUID sprintId,
            SprintAssignmentRequest request, String authorization) {
        List<Map<String, Object>> allIssues = fetchList("/projects/" + projectId + "/issues", authorization);
        List<Map<String, Object>> members = fetchList("/projects/" + projectId + "/members", authorization);
        List<Map<String, Object>> statuses = fetchWorkflowStatuses(projectId, authorization);
        Map<String, String> issueTypeNames = nameById(fetchList("/projects/" + projectId + "/issue-types", authorization));
        Map<String, String> priorityNames = nameById(fetchList("/projects/" + projectId + "/issue-priorities", authorization));

        Set<String> doneStatusIds = statuses.stream()
                .filter(status -> normalize(stringValue(status.get("category"))).contains("done"))
                .map(status -> stringValue(status.get("id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Map<String, Object>> workload = buildWorkloadSummary(members, allIssues, doneStatusIds);

        Set<String> requestedIssueIds = request == null || request.issueIds() == null
                ? Set.of()
                : request.issueIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        List<Map<String, Object>> targetIssues = allIssues.stream()
                .filter(issue -> {
                    String issueId = stringValue(issue.get("id"));
                    String currentSprintId = stringValue(issue.get("sprintId"));
                    if (!requestedIssueIds.isEmpty()) {
                        return requestedIssueIds.contains(issueId);
                    }
                    return sprintId.toString().equals(currentSprintId);
                })
                .filter(issue -> !doneStatusIds.contains(stringValue(issue.get("statusId"))))
                .sorted(Comparator.comparingInt(issue -> -intValue(issue.get("storyPoints"), 1)))
                .toList();

        Map<String, Map<String, Object>> mutableWorkload = workload.stream()
                .filter(item -> stringValue(item.get("memberId")) != null)
                .collect(Collectors.toMap(item -> stringValue(item.get("memberId")), LinkedHashMap::new,
                        (first, second) -> first, LinkedHashMap::new));

        List<Map<String, Object>> assignments = new ArrayList<>();
        for (Map<String, Object> issue : targetIssues) {
            String issueTypeId = stringValue(issue.get("issueTypeId"));
            String priorityId = stringValue(issue.get("priorityId"));
            String targetText = issueText(
                    stringValue(issue.get("title")),
                    stringValue(issue.get("description")),
                    issueTypeId,
                    firstNonBlank(firstNonBlank(issue, "issueTypeName", "typeName", "type"),
                            issueTypeNames.get(issueTypeId)),
                    priorityId,
                    firstNonBlank(firstNonBlank(issue, "priorityName", "priority"),
                            priorityNames.get(priorityId)),
                    labelsText(issue.get("labels")));
            List<Map<String, Object>> similarIssues = rankSimilarIssues(
                    projectId.toString(),
                    targetText,
                    safeEmbed(targetText),
                    allIssues,
                    doneStatusIds,
                    issueTypeNames,
                    priorityNames)
                    .stream()
                    .limit(5)
                    .map(match -> compactIssue(match.issue(), match.score()))
                    .toList();
            int points = Math.max(1, intValue(issue.get("storyPoints"), 1));
            Map<String, Object> assignee = chooseAssignee(new ArrayList<>(mutableWorkload.values()), similarIssues,
                    points);
            if (assignee == null) {
                break;
            }
            String memberId = stringValue(assignee.get("memberId"));
            assignee.put("openStoryPoints", intValue(assignee.get("openStoryPoints"), 0) + points);
            assignee.put("openIssueCount", intValue(assignee.get("openIssueCount"), 0) + 1);
            Map<String, Object> assignment = new LinkedHashMap<>();
            assignment.put("issueId", stringValue(issue.get("id")));
            assignment.put("issueKey", stringValue(issue.get("issueKey")));
            assignment.put("title", stringValue(issue.get("title")));
            assignment.put("suggestedAssigneeId", memberId);
            assignment.put("suggestedAssigneeName", stringValue(assignee.get("name")));
            assignment.put("storyPoints", points);
            assignment.put("similarIssues", similarIssues);
            int similarCount = intValue(assignee.get("similarIssueCount"), 0);
            if (similarCount > 0) {
                assignment.put("reason", "Nguoi nay tung lam " + similarCount
                        + " task Done tuong dong va van duoc can bang theo workload hien tai.");
            } else {
                assignment.put("reason", "Chua co lich su tuong dong du manh; uu tien workload thap nhat sau khi can bang sprint.");
            }
            assignments.add(assignment);
        }

        return new SprintAssignmentResponse(assignments, new ArrayList<>(mutableWorkload.values()), List.of(
                "Không tự cập nhật task; chỉ trả danh sách đề xuất để người dùng áp dụng.",
                "Task Done không được tính vào workload hiện tại."));
    }

    /**
     * Builds issue suggestion data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param members the members parameter
     * @param issues the issues parameter
     * @param doneStatusIds the done status ids parameter
     * @return the build workload summary result
     */
    List<Map<String, Object>> buildWorkloadSummary(List<Map<String, Object>> members, List<Map<String, Object>> issues,
            Set<String> doneStatusIds) {
        Map<String, Map<String, Object>> summary = new LinkedHashMap<>();
        for (Map<String, Object> member : members) {
            String memberId = firstNonBlank(member, "userId", "accountId", "id");
            if (memberId == null) {
                continue;
            }
            summary.put(memberId, new LinkedHashMap<>(Map.of(
                    "memberId", memberId,
                    "name", displayName(member),
                    "openIssueCount", 0,
                    "openStoryPoints", 0,
                    "completedIssueCount", 0)));
        }

        for (Map<String, Object> issue : issues) {
            String assigneeId = stringValue(issue.get("assigneeId"));
            if (assigneeId == null || !summary.containsKey(assigneeId)) {
                continue;
            }
            Map<String, Object> item = summary.get(assigneeId);
            if (doneStatusIds.contains(stringValue(issue.get("statusId")))) {
                item.put("completedIssueCount", intValue(item.get("completedIssueCount"), 0) + 1);
            } else {
                item.put("openIssueCount", intValue(item.get("openIssueCount"), 0) + 1);
                item.put("openStoryPoints", intValue(item.get("openStoryPoints"), 0)
                        + Math.max(0, intValue(issue.get("storyPoints"), 0)));
            }
        }

        return summary.values().stream()
                .sorted(Comparator.comparingInt(item -> intValue(item.get("openStoryPoints"), 0)))
                .toList();
    }

    /**
     * Builds issue suggestion suggestions.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param similarIssues the similar issues parameter
     * @return the suggest story points result
     */
    Integer suggestStoryPoints(List<Map<String, Object>> similarIssues) {
        List<Integer> points = similarIssues.stream()
                .map(issue -> intValue(issue.get("storyPoints"), -1))
                .filter(point -> point >= 0)
                .toList();
        if (points.isEmpty()) {
            return 3;
        }
        double average = points.stream().mapToInt(Integer::intValue).average().orElse(3);
        return nearestFibonacci((int) Math.round(average));
    }

    /**
     * Returns fetch list for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param path the path parameter
     * @param authorization the authorization parameter
     * @return the fetch list result
     */
    private List<Map<String, Object>> fetchList(String path, String authorization) {
        try {
            Map<?, ?> response = projectClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            Object data = response == null ? null : response.get("data");
            if (data instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .toList();
            }
        } catch (RestClientException ex) {
            log.warn("Unable to fetch project data from {}. Continuing with empty data: {}", path, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Unable to parse project data from {}. Continuing with empty data: {}", path, ex.getMessage());
        }
        return List.of();
    }

    /**
     * Returns fetch workflow statuses for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the fetch workflow statuses result
     */
    private List<Map<String, Object>> fetchWorkflowStatuses(UUID projectId, String authorization) {
        List<Map<String, Object>> workflows = fetchList("/projects/" + projectId + "/workflows", authorization);
        List<Map<String, Object>> statuses = new ArrayList<>();
        for (Map<String, Object> workflow : workflows) {
            String workflowId = stringValue(workflow.get("id"));
            if (workflowId != null) {
                statuses.addAll(fetchList("/projects/" + projectId + "/workflows/" + workflowId + "/statuses",
                        authorization));
            }
        }
        return statuses;
    }

    /**
     * Returns rank similar issues for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param requestText the request text parameter
     * @param requestEmbedding the request embedding parameter
     * @param issues the issues parameter
     * @param doneStatusIds the done status ids parameter
     * @param issueTypeNames the issue type names parameter
     * @param priorityNames the priority names parameter
     * @return the matching result collection
     */
    private List<SimilarIssue> rankSimilarIssues(String projectId,
            String requestText,
            List<Double> requestEmbedding,
            List<Map<String, Object>> issues,
            Set<String> doneStatusIds,
            Map<String, String> issueTypeNames,
            Map<String, String> priorityNames) {
        return issues.stream()
                .filter(issue -> isDoneIssue(issue, doneStatusIds))
                .filter(issue -> issue.get("storyPoints") != null)
                .map(issue -> {
                    String issueId = stringValue(issue.get("id"));
                    String issueTypeId = stringValue(issue.get("issueTypeId"));
                    String priorityId = stringValue(issue.get("priorityId"));
                    String text = issueText(
                            stringValue(issue.get("title")),
                            stringValue(issue.get("description")),
                            issueTypeId,
                            firstNonBlank(firstNonBlank(issue, "issueTypeName", "typeName", "type"),
                                    issueTypeNames.get(issueTypeId)),
                            priorityId,
                            firstNonBlank(firstNonBlank(issue, "priorityName", "priority"),
                                    priorityNames.get(priorityId)),
                            labelsText(issue.get("labels")));
                    double vectorScore = 0;
                    if (requestEmbedding != null && issueId != null) {
                        List<Double> issueEmbedding = cachedEmbedding(projectId, issueId, text);
                        vectorScore = cosine(requestEmbedding, issueEmbedding);
                    }
                    double textScore = textSimilarity(requestText, text);
                    double score = vectorScore > 0
                            ? Math.max(textScore, vectorScore * 0.72 + textScore * 0.28)
                            : textScore;
                    return new SimilarIssue(issue, score);
                })
                .filter(match -> match.score() > 0.08)
                .sorted(Comparator.comparingDouble(SimilarIssue::score).reversed())
                .toList();
    }

    /**
     * Returns cached embedding for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param issueId the issue id parameter
     * @param text the text parameter
     * @return the matching result collection
     */
    private List<Double> cachedEmbedding(String projectId, String issueId, String text) {
        Optional<IssueSuggestionVector> existing;
        try {
            existing = vectorRepository.findByProjectIdAndIssueId(projectId, issueId);
        } catch (DataAccessException ex) {
            log.warn("Issue suggestion vector cache unavailable, continuing without Mongo cache: {}", ex.getMessage());
            return safeEmbed(text);
        } catch (RuntimeException ex) {
            log.warn("Issue suggestion vector cache unavailable, continuing without Mongo cache: {}", ex.getMessage());
            return safeEmbed(text);
        }
        if (existing.isPresent() && Objects.equals(existing.get().getText(), text)
                && existing.get().getEmbedding() != null) {
            return existing.get().getEmbedding();
        }
        List<Double> embedding = safeEmbed(text);
        if (embedding != null) {
            try {
                IssueSuggestionVector vector = existing.orElseGet(IssueSuggestionVector::new);
                vector.setProjectId(projectId);
                vector.setIssueId(issueId);
                vector.setText(text);
                vector.setEmbedding(embedding);
                vector.setUpdatedAt(Instant.now());
                vectorRepository.save(vector);
            } catch (DataAccessException ex) {
                log.warn("Unable to save issue suggestion vector cache, continuing: {}", ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("Unable to save issue suggestion vector cache, continuing: {}", ex.getMessage());
            }
        }
        return embedding;
    }

    /**
     * Returns safe embed for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param text the text parameter
     * @return the matching result collection
     */
    private List<Double> safeEmbed(String text) {
        try {
            return embeddingService.embed(text);
        } catch (Exception ex) {
            log.debug("Embedding unavailable, falling back to lexical similarity: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Returns choose assignee for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param workload the workload parameter
     * @param similarIssues the similar issues parameter
     * @param suggestedStoryPoints the suggested story points parameter
     * @return the choose assignee result
     */
    Map<String, Object> chooseAssignee(List<Map<String, Object>> workload,
            List<Map<String, Object>> similarIssues,
            Integer suggestedStoryPoints) {
        Map<String, Integer> similarCountByAssignee = new HashMap<>();
        Map<String, Integer> samePointCountByAssignee = new HashMap<>();
        Map<String, Double> similarityScoreByAssignee = new HashMap<>();
        for (Map<String, Object> issue : similarIssues) {
            String assigneeId = stringValue(issue.get("assigneeId"));
            if (assigneeId == null || assigneeId.isBlank()) {
                continue;
            }
            similarCountByAssignee.merge(assigneeId, 1, Integer::sum);
            similarityScoreByAssignee.merge(assigneeId, doubleValue(issue.get("similarity"), 0), Double::sum);
            if (suggestedStoryPoints != null
                    && intValue(issue.get("storyPoints"), -1) == suggestedStoryPoints) {
                samePointCountByAssignee.merge(assigneeId, 1, Integer::sum);
            }
        }

        return workload.stream()
                .filter(item -> stringValue(item.get("memberId")) != null)
                .map(item -> {
                    Map<String, Object> enriched = new LinkedHashMap<>(item);
                    String memberId = stringValue(enriched.get("memberId"));
                    int similarCount = similarCountByAssignee.getOrDefault(memberId, 0);
                    int samePointCount = samePointCountByAssignee.getOrDefault(memberId, 0);
                    double similarityScore = similarityScoreByAssignee.getOrDefault(memberId, 0D);
                    int workloadPenalty = intValue(enriched.get("openStoryPoints"), 0) * 2
                            + intValue(enriched.get("openIssueCount"), 0) * 2
                            + Math.max(0, intValue(enriched.get("openStoryPoints"), 0) - 21) * 2;
                    int historyBonus = similarCount * 12
                            + samePointCount * 8
                            + (int) Math.round(similarityScore * 20)
                            + Math.min(5, intValue(enriched.get("completedIssueCount"), 0));
                    int score = workloadPenalty - historyBonus;
                    enriched.put("similarIssueCount", similarCount);
                    enriched.put("samePointHistoryCount", samePointCount);
                    enriched.put("similarityHistoryScore", Math.round(similarityScore * 100D) / 100D);
                    enriched.put("assignmentScore", score);
                    return enriched;
                })
                .min(Comparator
                        .comparingInt((Map<String, Object> item) -> intValue(item.get("assignmentScore"), 0))
                        .thenComparingInt(item -> intValue(item.get("openStoryPoints"), 0))
                        .thenComparingInt(item -> intValue(item.get("openIssueCount"), 0))
                        .thenComparing(item -> stringValue(item.get("name")), Comparator.nullsLast(String::compareTo)))
                .orElse(null);
    }

    /**
     * Returns compact issue for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param score the score parameter
     * @return the compact issue result
     */
    private Map<String, Object> compactIssue(Map<String, Object> issue, double score) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("id", issue.get("id"));
        compact.put("issueKey", issue.get("issueKey"));
        compact.put("title", issue.get("title"));
        compact.put("storyPoints", issue.get("storyPoints"));
        compact.put("assigneeId", issue.get("assigneeId"));
        compact.put("similarity", Math.round(score * 100.0) / 100.0);
        return compact;
    }

    /**
     * Returns top similar issue reason for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param similarIssues the similar issues parameter
     * @return the top similar issue reason result
     */
    private String topSimilarIssueReason(List<Map<String, Object>> similarIssues) {
        Map<String, Object> topIssue = similarIssues.getFirst();
        String issueKey = stringValue(topIssue.get("issueKey"));
        String title = stringValue(topIssue.get("title"));
        int storyPoints = intValue(topIssue.get("storyPoints"), 0);
        int similarityPercent = (int) Math.round(doubleValue(topIssue.get("similarity"), 0) * 100);
        String label = issueKey == null || issueKey.isBlank() ? title : issueKey + " · " + title;
        return label + " (" + storyPoints + " pts, similarity " + similarityPercent + "%)";
    }

    /**
     * Returns confidence for for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param similarIssues the similar issues parameter
     * @return the confidence for result
     */
    private double confidenceFor(List<Map<String, Object>> similarIssues) {
        if (similarIssues.isEmpty()) {
            return 0.35;
        }
        double topScore = ((Number) similarIssues.getFirst().getOrDefault("similarity", 0)).doubleValue();
        return Math.min(0.9, Math.max(0.45, topScore));
    }

    /**
     * Returns issue text for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param title the title parameter
     * @param description the description parameter
     * @param issueTypeId the issue type id parameter
     * @param issueTypeName the issue type name parameter
     * @param priorityId the priority id parameter
     * @param priorityName the priority name parameter
     * @param labels the labels parameter
     * @return the issue text result
     */
    private static String issueText(String title,
            String description,
            String issueTypeId,
            String issueTypeName,
            String priorityId,
            String priorityName,
            String labels) {
        String typeText = firstNonBlank(issueTypeName, issueTypeId);
        String priorityText = firstNonBlank(priorityName, priorityId);
        return String.join(" ", List.of(
                repeatField("title", title, 3),
                repeatField("description", description, 2),
                repeatField("type", typeText, 2),
                repeatField("priority", priorityText, 2),
                repeatField("labels", labels, 1))).trim();
    }

    /**
     * Returns repeat field for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param field the field parameter
     * @param value the value parameter
     * @param weight the weight parameter
     * @return the repeat field result
     */
    private static String repeatField(String field, String value, int weight) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String token = field + " " + value;
        return String.join(" ", java.util.Collections.nCopies(weight, token));
    }

    /**
     * Returns is done issue for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param doneStatusIds the done status ids parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private static boolean isDoneIssue(Map<String, Object> issue, Set<String> doneStatusIds) {
        String statusId = stringValue(issue.get("statusId"));
        if (statusId != null && doneStatusIds.contains(statusId)) {
            return true;
        }
        String statusCategory = normalize(stringValue(issue.get("statusCategory")));
        String statusName = normalize(stringValue(issue.get("statusName")));
        return isDoneText(statusCategory) || isDoneText(statusName);
    }

    /**
     * Returns is done text for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private static boolean isDoneText(String value) {
        return value.contains("done")
                || value.contains("completed")
                || value.contains("complete")
                || value.contains("hoan thanh");
    }

    /**
     * Returns labels text for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param labels the labels parameter
     * @return the labels text result
     */
    private static String labelsText(Object labels) {
        if (labels instanceof Iterable<?> iterable) {
            List<String> names = new ArrayList<>();
            for (Object label : iterable) {
                if (label instanceof Map<?, ?> map) {
                    Object name = map.get("name");
                    if (name != null) {
                        names.add(String.valueOf(name));
                    }
                } else if (label != null) {
                    names.add(String.valueOf(label));
                }
            }
            return String.join(" ", names);
        }
        return stringValue(labels);
    }

    /**
     * Returns name by id for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param items the items parameter
     * @return the name by id result
     */
    private static Map<String, String> nameById(List<Map<String, Object>> items) {
        Map<String, String> names = new HashMap<>();
        for (Map<String, Object> item : items) {
            String id = stringValue(item.get("id"));
            String name = stringValue(item.get("name"));
            if (id != null && name != null && !name.isBlank()) {
                names.put(id, name);
            }
        }
        return names;
    }

    /**
     * Returns cosine for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param left the left parameter
     * @param right the right parameter
     * @return the cosine result
     */
    private static double cosine(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) {
            return 0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /**
     * Returns text similarity for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param left the left parameter
     * @param right the right parameter
     * @return the text similarity result
     */
    static double textSimilarity(String left, String right) {
        double lexical = lexicalSimilarity(left, right);
        double phrase = phraseCoverage(left, right);
        double ngram = charNgramSimilarity(left, right);
        return Math.max(lexical, Math.max(phrase * 0.85, ngram * 0.65));
    }

    /**
     * Returns lexical similarity for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param left the left parameter
     * @param right the right parameter
     * @return the lexical similarity result
     */
    private static double lexicalSimilarity(String left, String right) {
        Set<String> leftWords = words(left);
        Set<String> rightWords = words(right);
        if (leftWords.isEmpty() || rightWords.isEmpty()) {
            return 0;
        }
        long intersection = leftWords.stream().filter(rightWords::contains).count();
        java.util.HashSet<String> unionWords = new java.util.HashSet<>(leftWords);
        unionWords.addAll(rightWords);
        long union = unionWords.size();
        return union == 0 ? 0 : (double) intersection / union;
    }

    /**
     * Returns words for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the matching result collection
     */
    private static Set<String> words(String value) {
        Set<String> result = java.util.Arrays.stream(normalize(value).split("\\s+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !STOP_WORDS.contains(word))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        List<String> synonyms = result.stream()
                .map(DOMAIN_SYNONYMS::get)
                .filter(Objects::nonNull)
                .toList();
        result.addAll(synonyms);
        return result;
    }

    /**
     * Returns phrase coverage for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param left the left parameter
     * @param right the right parameter
     * @return the phrase coverage result
     */
    private static double phraseCoverage(String left, String right) {
        List<String> leftWords = significantWords(left);
        List<String> rightWords = significantWords(right);
        if (leftWords.isEmpty() || rightWords.isEmpty()) {
            return 0;
        }
        String rightText = " " + String.join(" ", rightWords) + " ";
        long covered = leftWords.stream()
                .filter(word -> rightText.contains(" " + word + " "))
                .count();
        return (double) covered / leftWords.size();
    }

    /**
     * Returns char ngram similarity for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param left the left parameter
     * @param right the right parameter
     * @return the char ngram similarity result
     */
    private static double charNgramSimilarity(String left, String right) {
        Set<String> leftNgrams = charNgrams(left);
        Set<String> rightNgrams = charNgrams(right);
        if (leftNgrams.isEmpty() || rightNgrams.isEmpty()) {
            return 0;
        }
        long intersection = leftNgrams.stream().filter(rightNgrams::contains).count();
        java.util.HashSet<String> union = new java.util.HashSet<>(leftNgrams);
        union.addAll(rightNgrams);
        return union.isEmpty() ? 0 : (double) intersection / union.size();
    }

    /**
     * Returns char ngrams for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the matching result collection
     */
    private static Set<String> charNgrams(String value) {
        String normalized = normalize(value).replace(" ", "");
        if (normalized.length() < 4) {
            return Set.of();
        }
        Set<String> ngrams = new java.util.LinkedHashSet<>();
        for (int i = 0; i <= normalized.length() - 4; i++) {
            ngrams.add(normalized.substring(i, i + 4));
        }
        return ngrams;
    }

    /**
     * Returns significant words for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the matching result collection
     */
    private static List<String> significantWords(String value) {
        return words(value).stream().toList();
    }

    /**
     * Returns nearest fibonacci for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the nearest fibonacci result
     */
    private static int nearestFibonacci(int value) {
        return FIBONACCI_POINTS.stream()
                .min(Comparator.comparingInt(point -> Math.abs(point - value)))
                .orElse(3);
    }

    /**
     * Returns normalize for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the normalize result
     */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String noMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noMarks.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * Returns int value for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @param fallback the fallback parameter
     * @return the int value result
     */
    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Returns double value for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @param fallback the fallback parameter
     * @return the double value result
     */
    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Returns string value for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the string value result
     */
    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Returns string value for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the string value result
     */
    private static String stringValue(UUID value) {
        return value == null ? null : value.toString();
    }

    /**
     * Returns first non blank for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param values the values parameter
     * @return the first non blank result
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Returns first non blank for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param map the map parameter
     * @param keys the keys parameter
     * @return the first non blank result
     */
    private static String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = stringValue(map.get(key));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Returns display name for issue suggestion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param member the member parameter
     * @return the display name result
     */
    private static String displayName(Map<String, Object> member) {
        String name = firstNonBlank(member, "userName", "fullName", "name", "userEmail", "email");
        return name == null ? "Team member" : name;
    }

    private record SimilarIssue(Map<String, Object> issue, double score) {
    }
}
