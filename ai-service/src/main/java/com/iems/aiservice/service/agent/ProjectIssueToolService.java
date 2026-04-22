package com.iems.aiservice.service.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProjectIssueToolService {

    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("\\b([A-Z]+-\\d+)\\b");

    private final RestClient restClient;

    public ProjectIssueToolService(
            @Value("${ai.agent.project-base-url:http://localhost:8080/project-service}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String handleIssueQuery(String question, String projectId, String authorization) {
        if (projectId == null || projectId.isBlank()) {
            return "Can projectId de truy van danh sach issue/cong viec.";
        }

        String normalized = normalize(question);
        boolean myOnly = hasAnyPhrase(normalized, "my", "cua toi", "cua minh", "viec cua toi");
        boolean includeAll = hasAnyPhrase(normalized, "tat ca", "all", "het");
        List<Map<String, Object>> issues = getIssues(projectId, authorization, myOnly);
        Map<String, String> priorityById = getIssuePriorities(projectId, authorization);
        Map<String, String> typeById = getIssueTypes(projectId, authorization);
        Map<String, String> statusById = getWorkflowStatuses(projectId, authorization);
        LocalDate today = LocalDate.now();
        boolean needImportanceReason = hasAnyPhrase(normalized,
                "quan trong", "priority", "uu tien", "important", "quan trng", "quan trnog");
        boolean myAllAsBadge = myOnly && includeAll;

        List<Map<String, Object>> filtered = new ArrayList<>(issues);

        if (hasAnyPhrase(normalized, "bug", "loi", "error")) {
            filtered = filtered.stream()
                    .filter(issue -> {
                        String typeName = typeById.get(stringValue(issue.get("issueTypeId")));
                        if (typeName == null) {
                            return false;
                        }
                        String n = normalize(typeName);
                        return n.contains("bug") || n.contains("loi");
                    })
                    .collect(Collectors.toList());
        }

        if (needImportanceReason) {
            filtered = filtered.stream()
                    .filter(issue -> {
                        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
                        return !isDoneStatus(normalize(statusName));
                    })
                    .collect(Collectors.toList());

            filtered.sort(Comparator.comparingInt(
                    issue -> -issueImportanceScore(issue, priorityById, statusById, today)));
        }

        if (hasAnyPhrase(normalized, "hom nay", "today", "hnay")) {
            filtered = filtered.stream()
                    .filter(issue -> {
                        String dueDateRaw = stringValue(issue.get("dueDate"));
                        if (dueDateRaw == null || dueDateRaw.isBlank()) {
                            return false;
                        }
                        try {
                            LocalDate due = LocalDate.parse(dueDateRaw);
                            return !due.isAfter(today);
                        } catch (Exception ex) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            filtered.sort(Comparator.comparing(issue -> LocalDate.parse(stringValue(issue.get("dueDate")))));
        }

        if (filtered.isEmpty()) {
            return "Khong tim thay issue/cong viec phu hop.";
        }

        int limit = includeAll ? filtered.size() : Math.min(filtered.size(), 30);
        StringBuilder response = new StringBuilder();
        if (needImportanceReason) {
            response.append("Danh sach cong viec quan trong (badge):\n");
        } else if (myAllAsBadge) {
            response.append("Tat ca cong viec cua toi (badge):\n");
        } else {
            response.append("Tim thay ").append(filtered.size()).append(" issue. Hien thi ").append(limit)
                    .append(" muc dau:\n");
        }

        for (int i = 0; i < limit; i++) {
            Map<String, Object> issue = filtered.get(i);
            String key = stringValue(issue.get("issueKey"));
            String title = stringValue(issue.get("title"));
            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
            String dueDate = stringValue(issue.get("dueDate"));
            response.append(i + 1).append(". ").append(key == null ? "(no-key)" : key);

            if (needImportanceReason) {
                String reason = buildImportantReason(issue, priorityById, statusById, today);
                response.append(" | reason=").append(reason);
            } else if (!myAllAsBadge) {
                response.append(" | ")
                        .append(title == null ? "(no-title)" : title)
                        .append(" | status=")
                        .append(statusName)
                        .append(" | priority=")
                        .append(priorityName);
                if (dueDate != null && !dueDate.isBlank()) {
                    response.append(" | due=").append(dueDate);
                }
            }
            response.append("\n");
        }

        return response.toString().trim();
    }

    public String handleIssueAction(String question, String projectId, String authorization) {
        if (projectId == null || projectId.isBlank()) {
            return "Can projectId de thuc thi thay doi status issue.";
        }

        List<Map<String, Object>> issues = getIssues(projectId, authorization, false);
        if (issues.isEmpty()) {
            return "Khong co issue nao trong project de cap nhat.";
        }

        Map<String, String> statuses = getWorkflowStatuses(projectId, authorization);
        if (statuses.isEmpty()) {
            return "Khong tim thay workflow status trong project.";
        }

        String targetStatusPhrase = extractTargetStatus(question);
        Map.Entry<String, String> matchedStatus = findStatusByPhrase(statuses, targetStatusPhrase);
        if (matchedStatus == null) {
            return "Khong the xac dinh status dich tu cau lenh. Hay noi ro vi du: 'sang In Progress' hoac 'sang Done'.";
        }

        List<Map<String, Object>> targets = resolveTargets(question, issues, projectId, authorization);
        if (targets.isEmpty()) {
            return "Khong tim thay issue muc tieu de cap nhat status.";
        }

        int success = 0;
        List<String> failed = new ArrayList<>();
        int processed = 0;

        for (Map<String, Object> issue : targets) {
            if (processed >= 50) {
                failed.add("Da dung sau 50 issue de tranh tac dong qua lon trong 1 lenh.");
                break;
            }
            processed++;

            String issueId = stringValue(issue.get("id"));
            String issueKey = stringValue(issue.get("issueKey"));
            try {
                changeIssueStatus(projectId, issueId, matchedStatus.getKey(), authorization);
                success++;
            } catch (Exception ex) {
                failed.add((issueKey == null ? issueId : issueKey) + ": " + safeMessage(ex));
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("Da cap nhat status sang '")
                .append(matchedStatus.getValue())
                .append("' cho ")
                .append(success)
                .append("/")
                .append(processed)
                .append(" issue.");

        if (!failed.isEmpty()) {
            result.append("\nLoi:\n");
            failed.stream().limit(10).forEach(item -> result.append("- ").append(item).append("\n"));
        }

        return result.toString().trim();
    }

    public String handleIssueAnalysis(String question, String projectId, String authorization) {
        if (projectId == null || projectId.isBlank()) {
            return "Can projectId de phan tich cong viec.";
        }

        String normalized = normalize(question);
        boolean myOnly = hasAnyPhrase(normalized, "my", "cua toi", "cua minh", "viec cua toi");
        List<Map<String, Object>> issues = getIssues(projectId, authorization, myOnly);
        if (issues.isEmpty()) {
            return "Khong co issue nao de phan tich.";
        }

        Map<String, String> priorityById = getIssuePriorities(projectId, authorization);
        Map<String, String> statusById = getWorkflowStatuses(projectId, authorization);
        Map<String, String> typeById = getIssueTypes(projectId, authorization);

        LocalDate today = LocalDate.now();
        Map<String, Map<String, Object>> issueById = new HashMap<>();
        Map<String, Integer> childCountByParentId = new HashMap<>();
        List<Map<String, Object>> openIssues = new ArrayList<>();

        for (Map<String, Object> issue : issues) {
            String id = stringValue(issue.get("id"));
            if (id != null) {
                issueById.put(id, issue);
            }

            String parentId = stringValue(issue.get("parentId"));
            if (parentId != null && !parentId.isBlank()) {
                childCountByParentId.put(parentId, childCountByParentId.getOrDefault(parentId, 0) + 1);
            }

            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            if (!isDoneStatus(normalize(statusName))) {
                openIssues.add(issue);
            }
        }

        List<Map<String, Object>> taskTargets = selectTaskTargets(question, issues, openIssues,
                priorityById, statusById, today);
        if (taskTargets.isEmpty()) {
            return "Khong tim thay task phu hop de phan tich.";
        }

        boolean concisePriorityMode = hasAnyPhrase(normalized,
                "de xuat uu tien",
                "uu tien truoc",
                "phan tich cong viec hien tai",
                "phan tich uu tien",
                "uu tien cong viec");

        if (concisePriorityMode) {
            return buildConcisePriorityAnalysis(taskTargets, typeById, priorityById, statusById, today);
        }

        StringBuilder result = new StringBuilder();
        result.append("Mình đã phân tích nhanh các task ")
                .append(myOnly ? "của bạn" : "trong dự án")
                .append(". Mỗi task bên dưới đều có hướng làm ngắn gọn, làm được ngay:\n\n");

        int limit = Math.min(taskTargets.size(), 4);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> issue = taskTargets.get(i);
            String key = stringValue(issue.get("issueKey"));
            String title = stringValue(issue.get("title"));
            String typeName = typeById.getOrDefault(stringValue(issue.get("issueTypeId")), "unknown");
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            LocalDate due = parseDate(stringValue(issue.get("dueDate")));
            Integer storyPoints = parseIntValue(issue.get("storyPoints"));

            if (i > 0) {
                result.append("\n---\n\n");
            }

            result.append((key == null ? "(no-key)" : key))
                    .append(" | ")
                    .append(title == null ? "(no-title)" : title)
                    .append("\n\n")
                    .append(buildIssueTemplate(
                            issue,
                            typeName,
                            priorityName,
                            statusName,
                            due,
                            storyPoints,
                            today,
                            issueById,
                            childCountByParentId,
                            priorityById,
                            statusById,
                            typeById));
        }

        return result.toString().trim();
    }

    private List<Map<String, Object>> resolveTargets(String question,
            List<Map<String, Object>> issues,
            String projectId,
            String authorization) {
        List<String> keys = extractIssueKeys(question);
        if (!keys.isEmpty()) {
            Map<String, Map<String, Object>> byKey = new HashMap<>();
            for (Map<String, Object> issue : issues) {
                String key = stringValue(issue.get("issueKey"));
                if (key != null) {
                    byKey.put(normalize(key), issue);
                }
            }

            List<Map<String, Object>> targetByKey = new ArrayList<>();
            for (String key : keys) {
                Map<String, Object> found = byKey.get(normalize(key));
                if (found != null) {
                    targetByKey.add(found);
                }
            }
            return targetByKey;
        }

        String normalized = normalize(question);
        if (normalized.contains("dong loat") || normalized.contains("batch") || normalized.contains("tat ca")) {
            if (hasAnyPhrase(normalized, "bug", "loi", "error")) {
                Map<String, String> typeById = getIssueTypes(projectId, authorization);
                return issues.stream()
                        .filter(issue -> {
                            String typeName = typeById.get(stringValue(issue.get("issueTypeId")));
                            String n = normalize(typeName);
                            return n.contains("bug") || n.contains("loi");
                        })
                        .collect(Collectors.toList());
            }
            return issues;
        }

        return List.of();
    }

    private void changeIssueStatus(String projectId, String issueId, String statusId, String authorization) {
        restClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/projects/{projectId}/issues/{issueId}/status")
                        .queryParam("statusId", statusId)
                        .build(projectId, issueId))
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }

    private Map.Entry<String, String> findStatusByPhrase(Map<String, String> statusById, String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return null;
        }

        String target = normalize(phrase);
        for (Map.Entry<String, String> entry : statusById.entrySet()) {
            String name = normalize(entry.getValue());
            if (name.equals(target) || name.contains(target) || target.contains(name)) {
                return entry;
            }
        }

        if (target.contains("in progress") || target.contains("dang lam")) {
            for (Map.Entry<String, String> entry : statusById.entrySet()) {
                String name = normalize(entry.getValue());
                if (name.contains("in progress") || name.contains("dang lam") || name.contains("doing")) {
                    return entry;
                }
            }
        }

        if (target.contains("done") || target.contains("hoan thanh")) {
            for (Map.Entry<String, String> entry : statusById.entrySet()) {
                String name = normalize(entry.getValue());
                if (name.contains("done") || name.contains("hoan thanh") || name.contains("closed")) {
                    return entry;
                }
            }
        }

        if (target.contains("todo") || target.contains("to do") || target.contains("chua lam")) {
            for (Map.Entry<String, String> entry : statusById.entrySet()) {
                String name = normalize(entry.getValue());
                if (name.contains("todo") || name.contains("to do") || name.contains("chua lam")
                        || name.contains("open")) {
                    return entry;
                }
            }
        }

        return null;
    }

    private String extractTargetStatus(String question) {
        String normalized = normalize(question);
        if (normalized.contains("in progress") || normalized.contains("dang lam") || normalized.contains("doing")) {
            return "in progress";
        }
        if (normalized.contains("done") || normalized.contains("hoan thanh") || normalized.contains("closed")) {
            return "done";
        }
        if (normalized.contains("todo") || normalized.contains("to do") || normalized.contains("chua lam")
                || normalized.contains("open")) {
            return "todo";
        }

        Pattern p = Pattern.compile("(?:sang|to)\\s+([\\p{L}\\s-]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(question);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private List<String> extractIssueKeys(String text) {
        List<String> keys = new ArrayList<>();
        Matcher m = ISSUE_KEY_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getIssues(String projectId, String authorization, boolean myIssues) {
        String path = myIssues
                ? "/projects/{projectId}/issues/my-issues"
                : "/projects/{projectId}/issues";
        Map<String, Object> response = restClient.get()
                .uri(path, projectId)
                .header("Authorization", authorization)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return List.of();
        }

        Object data = response.get("data");
        if (!(data instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getIssueTypes(String projectId, String authorization) {
        Map<String, Object> response = restClient.get()
                .uri("/projects/{projectId}/issue-types", projectId)
                .header("Authorization", authorization)
                .retrieve()
                .body(Map.class);
        return mapIdToNameFromDataArray(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getIssuePriorities(String projectId, String authorization) {
        Map<String, Object> response = restClient.get()
                .uri("/projects/{projectId}/issue-priorities", projectId)
                .header("Authorization", authorization)
                .retrieve()
                .body(Map.class);
        return mapIdToNameFromDataArray(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getWorkflowStatuses(String projectId, String authorization) {
        Map<String, Object> workflowResponse = restClient.get()
                .uri("/projects/{projectId}/workflows", projectId)
                .header("Authorization", authorization)
                .retrieve()
                .body(Map.class);
        if (workflowResponse == null) {
            return Map.of();
        }

        Object workflowData = workflowResponse.get("data");
        if (!(workflowData instanceof List<?> workflows) || workflows.isEmpty()) {
            return Map.of();
        }

        String workflowId = null;
        for (Object workflow : workflows) {
            if (workflow instanceof Map<?, ?> map) {
                Object isDefault = map.get("isDefault");
                if (Boolean.TRUE.equals(isDefault)) {
                    workflowId = stringValue(map.get("id"));
                    break;
                }
            }
        }
        if (workflowId == null) {
            Object first = workflows.get(0);
            if (first instanceof Map<?, ?> firstMap) {
                workflowId = stringValue(firstMap.get("id"));
            }
        }
        if (workflowId == null || workflowId.isBlank()) {
            return Map.of();
        }

        Map<String, Object> statusResponse = restClient.get()
                .uri("/projects/{projectId}/workflows/{workflowId}/statuses", projectId, workflowId)
                .header("Authorization", authorization)
                .retrieve()
                .body(Map.class);

        return mapIdToNameFromDataArray(statusResponse);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> mapIdToNameFromDataArray(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (!(data instanceof List<?> list)) {
            return Map.of();
        }

        Map<String, String> byId = new LinkedHashMap<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String id = stringValue(map.get("id"));
                String name = stringValue(map.get("name"));
                if (id != null && name != null) {
                    byId.put(id, name);
                }
            }
        }
        return byId;
    }

    private int priorityWeight(String priorityName) {
        String normalized = normalize(priorityName);
        if (normalized.contains("critical") || normalized.contains("urgent") || normalized.contains("khan")
                || normalized.contains("cao")) {
            return 4;
        }
        if (normalized.contains("high")) {
            return 3;
        }
        if (normalized.contains("medium") || normalized.contains("trung")) {
            return 2;
        }
        if (normalized.contains("low") || normalized.contains("thap")) {
            return 1;
        }
        return 0;
    }

    private String safeMessage(Exception ex) {
        String msg = ex.getMessage();
        return msg == null || msg.isBlank() ? "request failed" : msg;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isDoneStatus(String normalizedStatus) {
        return normalizedStatus.contains("done")
                || normalizedStatus.contains("hoan thanh")
                || normalizedStatus.contains("closed")
                || normalizedStatus.contains("resolved");
    }

    private int issueImportanceScore(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
        int score = priorityWeight(priorityName) * 10;

        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String status = normalize(statusName);
        if (status.contains("block") || status.contains("stuck") || status.contains("hold")) {
            score += 15;
        }
        if (status.contains("progress") || status.contains("dang lam") || status.contains("doing")) {
            score += 4;
        }

        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        if (due != null) {
            if (due.isBefore(today)) {
                score += 20;
            } else if (due.isEqual(today)) {
                score += 12;
            } else if (due.minusDays(2).isBefore(today)) {
                score += 6;
            }
        }

        return score;
    }

    private String buildImportantReason(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<String> reasons = new ArrayList<>();

        String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
        int weight = priorityWeight(priorityName);
        if (weight >= 4) {
            reasons.add("priority rat cao");
        } else if (weight == 3) {
            reasons.add("priority cao");
        }

        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String status = normalize(statusName);
        if (status.contains("block") || status.contains("stuck") || status.contains("hold")) {
            reasons.add("dang bi blocker/stuck");
        }

        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        if (due != null) {
            if (due.isBefore(today)) {
                reasons.add("da qua han");
            } else if (due.isEqual(today)) {
                reasons.add("den han hom nay");
            } else if (due.minusDays(2).isBefore(today)) {
                reasons.add("sap den han");
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("anh huong truc tiep den tien do");
        }

        return String.join(", ", reasons);
    }

    private String buildTaskAction(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String status = normalize(statusName);
        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        int weight = priorityWeight(priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown"));

        if (status.contains("block") || status.contains("stuck") || status.contains("hold")) {
            return "go blocker ngay, gan owner va deadline clear blocker";
        }
        if (due != null && due.isBefore(today)) {
            return "escalate va cap nhat status trong ngay de xu ly no qua han";
        }
        if (due != null && due.isEqual(today)) {
            return "uu tien day task nay len dau ngay va chot ket qua truoc EOD";
        }
        if (weight >= 3) {
            return "dua vao nhom focus, cap nhat tien do 2 lan/ngay";
        }
        return "tiep tuc theo sprint plan va cap nhat tien do deu";
    }

    private List<Map<String, Object>> selectTaskTargets(String question,
            List<Map<String, Object>> allIssues,
            List<Map<String, Object>> openIssues,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<String> requestedKeys = extractIssueKeys(question);
        if (!requestedKeys.isEmpty()) {
            Map<String, Map<String, Object>> byKey = new HashMap<>();
            for (Map<String, Object> issue : allIssues) {
                String key = stringValue(issue.get("issueKey"));
                if (key != null) {
                    byKey.put(normalize(key), issue);
                }
            }

            List<Map<String, Object>> selected = new ArrayList<>();
            for (String key : requestedKeys) {
                Map<String, Object> found = byKey.get(normalize(key));
                if (found != null) {
                    selected.add(found);
                }
            }
            return selected;
        }

        return openIssues.stream()
                .sorted(Comparator.comparingInt(
                        issue -> -issueImportanceScore(issue, priorityById, statusById, today)))
                .limit(7)
                .collect(Collectors.toList());
    }

    private String buildComplexityInsight(Integer storyPoints, String descriptionNormalized, String typeName) {
        List<String> notes = new ArrayList<>();

        if (storyPoints != null) {
            if (storyPoints >= 8) {
                notes.add("khoi luong lon");
            } else if (storyPoints >= 5) {
                notes.add("khoi luong trung binh-cao");
            } else if (storyPoints >= 1) {
                notes.add("khoi luong nho");
            } else {
                notes.add("khoi luong rat nho");
            }
        } else {
            notes.add("chua co story point, can estimate");
        }

        String nType = normalize(typeName);
        if (nType.contains("bug") || nType.contains("loi")) {
            notes.add("task dang o nhom bug, can uu tien xac dinh impact");
        }

        if (descriptionNormalized == null || descriptionNormalized.isBlank()) {
            notes.add("description thieu thong tin");
        } else {
            if (descriptionNormalized.length() < 40) {
                notes.add("description ngan, de thieu context");
            }
            if (descriptionNormalized.contains("api") || descriptionNormalized.contains("db")
                    || descriptionNormalized.contains("migration") || descriptionNormalized.contains("auth")) {
                notes.add("co yeu to ky thuat can review ky");
            }
        }

        return String.join(", ", notes);
    }

    private String buildTaskRiskInsight(String title,
            String descriptionNormalized,
            LocalDate due,
            String statusName,
            String priorityName,
            LocalDate today) {
        List<String> risks = new ArrayList<>();
        String nStatus = normalize(statusName);
        String nTitle = normalize(title);
        int pWeight = priorityWeight(priorityName);

        if (due != null && due.isBefore(today)) {
            risks.add("dang qua han");
        } else if (due != null && due.isEqual(today)) {
            risks.add("den han hom nay");
        }

        if (pWeight >= 3) {
            risks.add("priority cao nen anh huong lon neu tre");
        }

        if (nStatus.contains("block") || nStatus.contains("stuck") || nStatus.contains("hold")) {
            risks.add("dang blocker/stuck");
        }

        if (nTitle.contains("fix") || nTitle.contains("bug") || nTitle.contains("error") || nTitle.contains("loi")) {
            risks.add("co dau hieu issue ve chat luong/defect");
        }

        if (descriptionNormalized != null && (descriptionNormalized.contains("security")
                || descriptionNormalized.contains("auth")
                || descriptionNormalized.contains("payment"))) {
            risks.add("lien quan luong nhay cam, can test/regression");
        }

        if (risks.isEmpty()) {
            risks.add("rui ro trung binh, theo doi tien do thuong xuyen");
        }

        return String.join(", ", risks);
    }

    private String buildParentChildInsight(Map<String, Object> issue,
            Map<String, Map<String, Object>> issueById,
            Map<String, Integer> childCountByParentId) {
        String issueId = stringValue(issue.get("id"));
        String parentId = stringValue(issue.get("parentId"));
        List<String> insights = new ArrayList<>();

        if (parentId != null && !parentId.isBlank()) {
            Map<String, Object> parent = issueById.get(parentId);
            String parentKey = parent == null ? parentId : stringValue(parent.get("issueKey"));
            String parentTitle = parent == null ? null : stringValue(parent.get("title"));
            insights.add("la task con cua " + (parentKey == null ? "parent-unknown" : parentKey)
                    + (parentTitle == null || parentTitle.isBlank() ? "" : " (" + parentTitle + ")"));
        }

        if (issueId != null) {
            int childCount = childCountByParentId.getOrDefault(issueId, 0);
            if (childCount > 0) {
                insights.add("dang la parent cua " + childCount + " task con");
            }
        }

        if (insights.isEmpty()) {
            insights.add("khong co lien ket parent/child ro rang");
        }

        return String.join(", ", insights);
    }

    private String buildTaskUserAction(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            Map<String, String> typeById,
            Map<String, Map<String, Object>> issueById,
            Map<String, Integer> childCountByParentId,
            LocalDate today) {
        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String nStatus = normalize(statusName);
        String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
        String typeName = typeById.getOrDefault(stringValue(issue.get("issueTypeId")), "unknown");
        int pWeight = priorityWeight(priorityName);
        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        String description = normalize(stringValue(issue.get("description")));

        String parentId = stringValue(issue.get("parentId"));
        if (parentId != null && !parentId.isBlank()) {
            Map<String, Object> parent = issueById.get(parentId);
            String parentKey = parent == null ? "parent" : stringValue(parent.get("issueKey"));
            return "dong bo scope voi " + parentKey + ", cap nhat tien do task con va bao cao dependency som";
        }

        String issueId = stringValue(issue.get("id"));
        if (issueId != null && childCountByParentId.getOrDefault(issueId, 0) > 0) {
            return "chia ro owner/timebox cho task con, theo doi blocker cua tung subtask moi ngay";
        }

        String nType = normalize(typeName);
        if (nStatus.contains("block") || nStatus.contains("stuck") || nStatus.contains("hold")) {
            return "xac dinh nguyen nhan blocker, dat nguoi go blocker va han giai quyet trong ngay";
        }
        if (due != null && due.isBefore(today)) {
            return "uu tien xu ly gap va cap nhat ETA moi cho stakeholder";
        }
        if ((nType.contains("bug") || nType.contains("loi")) && pWeight >= 3) {
            return "thuc hien fix + regression test ngay, sau do thong bao ket qua cho team";
        }
        if (description == null || description.isBlank() || description.length() < 40) {
            return "bo sung acceptance criteria, impact va test case truoc khi tiep tuc implement";
        }
        if (pWeight >= 3) {
            return "dua vao danh sach top priority va update tien do 2 lan/ngay";
        }
        return "tiep tuc theo sprint plan, dam bao commit scope va cap nhat progress deu";
    }

    private Integer parseIntValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildIssueTemplate(Map<String, Object> issue,
            String typeName,
            String priorityName,
            String statusName,
            LocalDate due,
            Integer storyPoints,
            LocalDate today,
            Map<String, Map<String, Object>> issueById,
            Map<String, Integer> childCountByParentId,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            Map<String, String> typeById) {
        String title = stringValue(issue.get("title"));
        String descriptionRaw = stringValue(issue.get("description"));
        String description = normalize(descriptionRaw);
        String parentChild = buildParentChildInsight(issue, issueById, childCountByParentId);
        String riskInsight = buildTaskRiskInsight(title, description, due, statusName, priorityName, today);
        String userAction = buildTaskUserAction(issue, priorityById, statusById, typeById, issueById,
                childCountByParentId, today);
        String complexity = buildComplexityInsight(storyPoints, description, typeName);

        StringBuilder out = new StringBuilder();
        out.append("1. Tóm tắt:\n")
                .append("- Cần làm: ")
                .append(title == null ? "task nay" : title)
                .append(".\n")
                .append("- Mục tiêu: ")
                .append(summaryBusinessGoal(typeName, descriptionRaw))
                .append("\n\n");

        out.append("2. Phân tích kỹ thuật:\n")
                .append("- Loại bài toán: ")
                .append(classifyProblemType(typeName, description, title))
                .append("\n")
                .append("- Thành phần bị ảnh hưởng: ")
                .append(classifyImpactedComponents(typeName, description, title))
                .append("\n")
                .append("- Parent / Child: ")
                .append(parentChild)
                .append("\n")
                .append("- Độ phức tạp: ")
                .append(complexity)
                .append("\n\n");

        out.append("3. Hướng dẫn thực hiện:\n")
                .append(buildImplementationSteps(issue, typeName, priorityName, statusName, due, storyPoints,
                        descriptionRaw, parentChild))
                .append("\n\n");

        out.append("4. Breakdown:\n")
                .append(buildBreakdownItems(issue, storyPoints, parentChild, typeName))
                .append("\n\n");

        out.append("5. Rủi ro:\n")
                .append("- ").append(riskInsight).append("\n")
                .append("- Cần review kỹ xử lý lỗi và timeout.\n\n");

        out.append("6. Test cần làm:\n")
                .append(buildTestPlan(typeName, description, due))
                .append("\n\n");

        out.append("7. Gợi ý:\n")
                .append("- ").append(userAction).append("\n")
                .append("- Chốt Definition of Done rõ ràng trước khi merge.");

        return out.toString();
    }

    private String buildConcisePriorityAnalysis(List<Map<String, Object>> taskTargets,
            Map<String, String> typeById,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<Map<String, Object>> sorted = taskTargets.stream()
                .sorted(Comparator.comparingInt(issue -> -compactPriorityScore(issue, priorityById, statusById, today)))
                .limit(5)
                .collect(Collectors.toList());

        StringBuilder out = new StringBuilder();
        out.append("Ưu tiên công việc hiện tại (ngắn gọn):\n");

        for (int i = 0; i < sorted.size(); i++) {
            Map<String, Object> issue = sorted.get(i);
            String key = stringValue(issue.get("issueKey"));
            String title = stringValue(issue.get("title"));
            String typeName = typeById.getOrDefault(stringValue(issue.get("issueTypeId")), "Task");
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "Unknown");
            Integer storyPoints = parseIntValue(issue.get("storyPoints"));

            String whatToDo = buildShortWhatToDo(typeName, title);
            String reason = buildShortPriorityReason(issue, priorityName, storyPoints, statusById, today);

            out.append(i + 1)
                    .append(". ")
                    .append(key == null ? "(no-key)" : key)
                    .append(" | ")
                    .append(title == null ? "(no-title)" : title)
                    .append("\n")
                    .append("   - Làm gì: ").append(whatToDo).append("\n")
                    .append("   - Vì sao ưu tiên: ").append(reason).append("\n");
        }

        return out.toString().trim();
    }

    private String summaryBusinessGoal(String typeName, String descriptionRaw) {
        String nType = normalize(typeName);
        String nDesc = normalize(descriptionRaw);
        if (nType.contains("bug") || nType.contains("loi")) {
            return "khac phuc loi, khoi phuc hanh vi dung va giam nguy co tai phat";
        }
        if (nDesc.contains("auth") || nDesc.contains("login") || nDesc.contains("token")) {
            return "bao dam xac thuc/phan quyen dung va an toan";
        }
        if (nDesc.contains("api") || nDesc.contains("integration") || nDesc.contains("sync")) {
            return "dam bao luong tich hop va dong bo du lieu on dinh";
        }
        return "hoan thanh tinh nang theo dung scope va chat luong";
    }

    private int compactPriorityScore(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        int score = issueImportanceScore(issue, priorityById, statusById, today);
        Integer storyPoints = parseIntValue(issue.get("storyPoints"));
        if (storyPoints != null && storyPoints >= 8) {
            score += 10;
        } else if (storyPoints != null && storyPoints >= 5) {
            score += 5;
        }

        int age = issueAgeDays(issue);
        if (age >= 14) {
            score += 10;
        } else if (age >= 7) {
            score += 5;
        }

        String desc = normalize(stringValue(issue.get("description")));
        if (desc.contains("integration") || desc.contains("migration") || desc.contains("auth")
                || desc.contains("payment")) {
            score += 5;
        }

        return score;
    }

    private String buildShortWhatToDo(String typeName, String title) {
        String nType = normalize(typeName);
        if (nType.contains("bug") || nType.contains("loi")) {
            return "Fix lỗi chính và xác nhận không tái phát bằng regression test.";
        }
        if (nType.contains("story") || nType.contains("epic")) {
            return "Tách scope, chốt acceptance criteria, rồi triển khai luồng cốt lõi trước.";
        }
        String shortTitle = title == null ? "task hiện tại" : title;
        return "Hoàn thành luồng chính của \"" + shortTitle + "\" và cập nhật trạng thái trong ngày.";
    }

    private String buildShortPriorityReason(Map<String, Object> issue,
            String priorityName,
            Integer storyPoints,
            Map<String, String> statusById,
            LocalDate today) {
        List<String> reasons = new ArrayList<>();
        int pWeight = priorityWeight(priorityName);
        int score = compactPriorityEvidenceScore(issue, priorityName, statusById, today);

        reasons.add("điểm ưu tiên=" + score);

        if (pWeight >= 4) {
            reasons.add("priority rất cao (" + priorityName + ")");
        } else if (pWeight == 3) {
            reasons.add("priority cao (" + priorityName + ")");
        }

        if (storyPoints != null && storyPoints >= 8) {
            reasons.add("SP=" + storyPoints + " (lớn)");
        } else if (storyPoints != null && storyPoints >= 5) {
            reasons.add("SP=" + storyPoints + " (tương đối cao)");
        }

        int age = issueAgeDays(issue);
        if (age >= 14) {
            reasons.add("đã tồn tại lâu: " + age + " ngày");
        } else if (age >= 7) {
            reasons.add("đang tồn đọng: " + age + " ngày");
        }

        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String nStatus = normalize(statusName);
        if (nStatus.contains("block") || nStatus.contains("stuck") || nStatus.contains("hold")) {
            reasons.add("status hiện tại=" + statusName + " (blocker/stuck)");
        }

        String desc = normalize(stringValue(issue.get("description")));
        if (desc.contains("integration") || desc.contains("migration") || desc.contains("auth")
                || desc.contains("payment")) {
            reasons.add("độ khó kỹ thuật cao (mô tả có integration/migration/auth/payment)");
        }

        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        if (due != null && due.isBefore(today)) {
            long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(due, today);
            reasons.add("đã quá hạn " + overdueDays + " ngày (due=" + due + ")");
        } else if (due != null && due.isEqual(today)) {
            reasons.add("đến hạn hôm nay (due=" + due + ")");
        }

        if (reasons.isEmpty()) {
            reasons.add("ảnh hưởng trực tiếp tiến độ sprint (chưa có chỉ báo rủi ro mạnh khác)");
        }
        return String.join(", ", reasons) + ".";
    }

    private int compactPriorityEvidenceScore(Map<String, Object> issue,
            String priorityName,
            Map<String, String> statusById,
            LocalDate today) {
        int score = priorityWeight(priorityName) * 10;

        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String status = normalize(statusName);
        if (status.contains("block") || status.contains("stuck") || status.contains("hold")) {
            score += 15;
        }

        Integer storyPoints = parseIntValue(issue.get("storyPoints"));
        if (storyPoints != null && storyPoints >= 8) {
            score += 10;
        } else if (storyPoints != null && storyPoints >= 5) {
            score += 5;
        }

        int age = issueAgeDays(issue);
        if (age >= 14) {
            score += 10;
        } else if (age >= 7) {
            score += 5;
        }

        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        if (due != null && due.isBefore(today)) {
            score += 20;
        } else if (due != null && due.isEqual(today)) {
            score += 10;
        }

        String desc = normalize(stringValue(issue.get("description")));
        if (desc.contains("integration") || desc.contains("migration") || desc.contains("auth")
                || desc.contains("payment")) {
            score += 5;
        }

        return score;
    }

    private int issueAgeDays(Map<String, Object> issue) {
        String rawCreatedAt = stringValue(issue.get("createdAt"));
        if (rawCreatedAt == null || rawCreatedAt.isBlank()) {
            return 0;
        }

        LocalDate createdDate = parseFlexibleDate(rawCreatedAt);
        if (createdDate == null) {
            return 0;
        }
        return (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(createdDate, LocalDate.now()));
    }

    private LocalDate parseFlexibleDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            if (value.length() >= 10) {
                return LocalDate.parse(value.substring(0, 10));
            }
            return LocalDate.parse(value);
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String classifyProblemType(String typeName, String description, String title) {
        String nType = normalize(typeName);
        String nDesc = description == null ? "" : description;
        String nTitle = normalize(title);
        List<String> tags = new ArrayList<>();

        if (nType.contains("bug") || nType.contains("loi") || nTitle.contains("fix") || nTitle.contains("error")) {
            tags.add("Bug fix");
        } else {
            tags.add("Feature/Task");
        }
        if (nDesc.contains("auth") || nDesc.contains("token") || nDesc.contains("permission")) {
            tags.add("Auth/Security");
        }
        if (nDesc.contains("api") || nDesc.contains("integration") || nDesc.contains("webhook")) {
            tags.add("Integration/API");
        }
        if (nDesc.contains("ui") || nDesc.contains("frontend") || nDesc.contains("screen")) {
            tags.add("UI/UX");
        }
        if (nDesc.contains("db") || nDesc.contains("migration") || nDesc.contains("query")) {
            tags.add("Data/DB");
        }

        return String.join(" / ", tags);
    }

    private String classifyImpactedComponents(String typeName, String description, String title) {
        String n = (description == null ? "" : description) + " " + normalize(typeName) + " " + normalize(title);
        List<String> components = new ArrayList<>();

        components.add("BE logic");
        if (n.contains("api") || n.contains("integration") || n.contains("endpoint")) {
            components.add("API contract");
        }
        if (n.contains("db") || n.contains("migration") || n.contains("query")) {
            components.add("DB schema/query");
        }
        if (n.contains("ui") || n.contains("frontend") || n.contains("screen")) {
            components.add("FE flow");
        }
        if (n.contains("auth") || n.contains("token") || n.contains("permission")) {
            components.add("Security layer");
        }

        return String.join(", ", components);
    }

    private String buildImplementationSteps(Map<String, Object> issue,
            String typeName,
            String priorityName,
            String statusName,
            LocalDate due,
            Integer storyPoints,
            String descriptionRaw,
            String parentChild) {
        StringBuilder sb = new StringBuilder();
        String nType = normalize(typeName);

        sb.append("- Bước 1:\n")
                .append("  + Làm gì: Chốt scope và tiêu chí hoàn thành từ title/description, xác nhận phụ thuộc parent-child.\n")
                .append("  + Output mong đợi: Checklist việc phải làm + thứ tự ưu tiên.\n")
                .append("  + API/logic/DB: Chốt contract và rule trước khi code.\n");

        sb.append("- Bước 2:\n")
                .append("  + Làm gì: Thiết kế thay đổi chính ở API/service/repository (validation + error code).\n")
                .append("  + Output mong đợi: Thiết kế ngắn gọn, dev khác đọc là làm được ngay.\n")
                .append("  + API/logic/DB: Xác định endpoint/field/query bị ảnh hưởng.\n");

        sb.append("- Bước 3:\n")
                .append("  + Làm gì: Implement luồng chính end-to-end, sau đó cập nhật FE nếu đổi contract.\n")
                .append("  + Output mong đợi: Chạy được happy path và xử lý lỗi cơ bản.\n")
                .append("  + API/logic/DB: Mapping DTO/entity đúng, migration/index nếu cần.\n");

        sb.append("- Bước 4:\n")
                .append("  + Làm gì: Viết test trọng yếu và chạy regression ở các luồng liên quan.\n")
                .append("  + Output mong đợi: Test pass, không vỡ tính năng cũ.\n")
                .append("  + API/logic/DB: Cover validation, permission, consistency.\n");

        if ((storyPoints != null && storyPoints >= 8) || nType.contains("epic") || nType.contains("story")) {
            sb.append("- Bước bổ sung (task lớn):\n")
                    .append("  + Làm gì: Chia milestone nhỏ để giao từng phần.\n")
                    .append("  + Output mong đợi: Có plan theo phase, kiểm soát rủi ro tốt hơn.\n")
                    .append("  + API/logic/DB: Ưu tiên phần tạo contract ổn định trước.\n");
        }

        if (due != null) {
            sb.append("- Ghi chú deadline: due=").append(due).append(", priority=").append(priorityName)
                    .append(", status hiện tại=").append(statusName).append(".\n");
        }
        sb.append("- Liên hệ parent/child: ").append(parentChild).append(".");

        return sb.toString();
    }

    private boolean hasAnyPhrase(String text, String... phrases) {
        for (String phrase : phrases) {
            if (matchesPhraseFuzzy(text, normalize(phrase))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPhraseFuzzy(String text, String phrase) {
        if (text.contains(phrase)) {
            return true;
        }

        String compactText = text.replace(" ", "");
        String compactPhrase = phrase.replace(" ", "");
        if (compactText.contains(compactPhrase)) {
            return true;
        }

        String[] textWords = text.split("\\s+");
        String[] phraseWords = phrase.split("\\s+");

        for (String phraseWord : phraseWords) {
            boolean found = false;
            for (String textWord : textWords) {
                if (isSimilarToken(textWord, phraseWord)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private boolean isSimilarToken(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        int maxLen = Math.max(a.length(), b.length());
        int threshold = maxLen <= 5 ? 1 : 2;
        return levenshteinDistance(a, b) <= threshold;
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private String buildBreakdownItems(Map<String, Object> issue,
            Integer storyPoints,
            String parentChild,
            String typeName) {
        List<String> items = new ArrayList<>();
        String nType = normalize(typeName);

        items.add("- Task 1: Clarify requirement + cập nhật acceptance criteria.");
        items.add("- Task 2: Implement logic chính + validation.");
        items.add("- Task 3: Cập nhật API contract/DTO + error code.");
        items.add("- Task 4: Viết test + regression.");

        if (storyPoints != null && storyPoints >= 8) {
            items.add("- Task 5: Tách thành subtask theo module để review độc lập.");
        }
        if (parentChild.contains("parent") || parentChild.contains("task con")) {
            items.add("- Task phụ: Đồng bộ dependency với parent/child trước khi close.");
        }
        if (nType.contains("bug") || nType.contains("loi")) {
            items.add("- Task phụ: Viết root cause note + prevention checklist.");
        }

        return String.join("\n", items);
    }

    private String buildTestPlan(String typeName, String description, LocalDate due) {
        String nType = normalize(typeName);
        String nDesc = description == null ? "" : description;
        StringBuilder sb = new StringBuilder();

        sb.append("- Unit test: cover business rule chính, validation, error handling.\n")
                .append("- Integration test: cover endpoint-service-repository và mapping dữ liệu.\n")
                .append("- Case quan trọng cần cover:\n")
                .append("  + Happy path (dữ liệu hợp lệ).\n")
                .append("  + Invalid input/permission denied/not found.\n")
                .append("  + Concurrent update hoặc duplicate request.\n");

        if (nType.contains("bug") || nType.contains("loi")) {
            sb.append("  + Regression case cho bug gốc để tránh tái phát.\n");
        }
        if (nDesc.contains("auth") || nDesc.contains("token") || nDesc.contains("permission")) {
            sb.append("  + Security case: token invalid/expired, role không đủ quyền.\n");
        }
        if (due != null) {
            sb.append("  + Smoke test nhanh theo deadline due=").append(due).append(" trước khi merge.\n");
        }

        return sb.toString().trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lowered = value.toLowerCase(Locale.ROOT).trim();
        String decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }
}
