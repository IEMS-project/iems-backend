package com.iems.aiservice.service.agent;

import com.iems.aiservice.model.agent.AgentDecision;
import com.iems.aiservice.model.agent.AgentIntent;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AgentIntentRouterService {

    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("\\b[A-Z][A-Z0-9]*-\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Set<String> ISSUE_ACTION_TERMS = Set.of(
            "tao issue", "create issue", "cap nhat issue", "update issue", "chuyen trang thai", "assign",
            "gan", "doi assignee", "them comment", "close issue", "mo issue", "chuyen issue", "doi status",
            "chuyen sang", "move issue", "cap nhat trang thai", "doi trang thai", "mark done",
            "doi trang thai thanh", "cap nhat priority", "doi priority");

    private static final Set<String> REPORT_TERMS = Set.of(
            "tom tat tien do", "dem so issue", "thong ke", "thong ke theo status", "thong ke theo priority",
            "phan tich rui ro", "lap ke hoach hom nay", "de xuat viec can lam", "bao nhieu issue",
            "tinh trang du an", "tom tat tinh trang", "suc khoe du an", "bao cao", "report", "summary",
            "count issue", "status va priority", "ai dang qua tai", "qua tai", "deadline", "han chot");

    private static final Set<String> ISSUE_ANALYSIS_TERMS = Set.of(
            "phan tich", "root cause", "nguyen nhan", "rui ro", "risk", "duplicate", "trung lap", "blocker",
            "stuck", "uu tien", "priority", "phan tich cong viec", "phan tich task", "workload", "tong quan cong viec",
            "lap ke hoach", "ke hoach hom nay", "de xuat", "buoc tiep theo", "hanh dong tiep theo",
            "day du an", "risk review", "standup", "bao cao standup", "tom tat tien do", "tinh hinh cong viec",
            "grooming", "lam ro", "thieu mo ta", "acceptance criteria", "test case", "task mo ho",
            "issue mo ho", "chat luong task");

    private static final Set<String> SPRINT_TERMS = Set.of(
            "sprint", "burndown", "worklog", "tien do", "velocity", "backlog", "qua han", "deadline",
            "bao cao tien do", "progress summary");

    private static final Set<String> ISSUE_QUERY_TERMS = Set.of(
            "issue", "task", "cong viec", "my issues", "danh sach", "status", "assignee", "reporter",
            "hom nay", "quan trong", "uu tien", "important", "today", "viec nao", "can lam gi");

    public AgentDecision route(String question) {
        if (question == null || question.isBlank()) {
            return new AgentDecision(AgentIntent.GENERAL_CHAT, 0.5, "empty_question");
        }

        String normalized = normalize(question);

        if (isExplicitIssueUpdate(question, normalized)) {
            return new AgentDecision(AgentIntent.ISSUE_UPDATE, 0.88, "matched_issue_action_terms");
        }

        if (containsAnyFuzzy(normalized, REPORT_TERMS)) {
            if (containsAnyFuzzy(normalized, Set.of("lap ke hoach hom nay", "ke hoach hom nay", "5 viec uu tien", "top 5"))) {
                return new AgentDecision(AgentIntent.DAILY_PLAN, 0.92, "matched_daily_plan_terms");
            }
            if (containsAnyFuzzy(normalized, Set.of("rui ro", "risk", "blocker"))) {
                return new AgentDecision(AgentIntent.RISK_ANALYSIS, 0.92, "matched_risk_terms");
            }
            if (containsAnyFuzzy(normalized, Set.of("workload", "qua tai", "ai dang qua tai"))) {
                return new AgentDecision(AgentIntent.MEMBER_WORKLOAD, 0.92, "matched_workload_terms");
            }
            if (containsAnyFuzzy(normalized, Set.of("deadline", "han chot", "qua han"))) {
                return new AgentDecision(AgentIntent.DEADLINE_CHECK, 0.92, "matched_deadline_terms");
            }
            return new AgentDecision(AgentIntent.PROJECT_SUMMARY, 0.9, "matched_report_terms");
        }

        if (containsAnyFuzzy(normalized, ISSUE_ANALYSIS_TERMS)) {
            if (containsAnyFuzzy(normalized, Set.of("workload", "qua tai", "ai dang qua tai"))) {
                return new AgentDecision(AgentIntent.MEMBER_WORKLOAD, 0.86, "matched_workload_terms");
            }
            if (containsAnyFuzzy(normalized, Set.of("deadline", "qua han", "tre deadline", "han chot"))) {
                return new AgentDecision(AgentIntent.DEADLINE_CHECK, 0.86, "matched_deadline_terms");
            }
            if (containsAnyFuzzy(normalized, Set.of("lap ke hoach", "ke hoach hom nay", "de xuat viec can lam"))) {
                return new AgentDecision(AgentIntent.DAILY_PLAN, 0.86, "matched_daily_plan_terms");
            }
            if (containsAnyFuzzy(normalized, Set.of("rui ro", "risk", "blocker"))) {
                return new AgentDecision(AgentIntent.RISK_ANALYSIS, 0.86, "matched_risk_terms");
            }
            return new AgentDecision(AgentIntent.PROJECT_SUMMARY, 0.82, "matched_issue_analysis_terms");
        }
        if (containsAnyFuzzy(normalized, SPRINT_TERMS)) {
            return new AgentDecision(AgentIntent.SPRINT_REPORT, 0.8, "matched_sprint_terms");
        }
        if (containsAnyFuzzy(normalized, ISSUE_QUERY_TERMS)) {
            return new AgentDecision(AgentIntent.ISSUE_SEARCH, 0.75, "matched_issue_query_terms");
        }

        return new AgentDecision(AgentIntent.GENERAL_CHAT, 0.68, "fallback_general_chat");
    }

    private static boolean isExplicitIssueUpdate(String original, String normalized) {
        boolean hasAction = containsAnyFuzzy(normalized, ISSUE_ACTION_TERMS);
        if (!hasAction) {
            return false;
        }

        boolean hasTargetIssue = ISSUE_KEY_PATTERN.matcher(original).find();
        boolean hasDirectObject = normalized.contains("task nay") || normalized.contains("issue nay")
                || normalized.contains("cong viec nay");
        boolean hasTargetStatus = normalized.contains(" done") || normalized.endsWith("done")
                || normalized.contains("in progress") || normalized.contains("dang lam")
                || normalized.contains("todo") || normalized.contains("to do")
                || normalized.contains("high") || normalized.contains("medium") || normalized.contains("low")
                || normalized.contains("cao") || normalized.contains("trung binh") || normalized.contains("thap");
        boolean hasAssignmentTarget = normalized.contains(" gan ") || normalized.startsWith("gan ")
                || normalized.contains("assign") || normalized.contains(" cho ");

        return (hasTargetIssue || hasDirectObject) && (hasTargetStatus || hasAssignmentTarget);
    }

    private static String normalize(String text) {
        String lowered = text.toLowerCase().trim();
        String decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }

    private static boolean containsAnyFuzzy(String text, Set<String> terms) {
        for (String term : terms) {
            if (matchesTermFuzzy(text, term)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesTermFuzzy(String text, String term) {
        if (text.contains(term)) {
            return true;
        }

        String compactText = text.replace(" ", "");
        String compactTerm = term.replace(" ", "");
        if (compactText.contains(compactTerm)) {
            return true;
        }

        String[] textWords = text.split("\\s+");
        String[] termWords = term.split("\\s+");

        if (termWords.length == 1) {
            for (String w : textWords) {
                if (isSimilarWord(w, termWords[0])) {
                    return true;
                }
            }
            return false;
        }

        for (String termWord : termWords) {
            boolean found = false;
            for (String textWord : textWords) {
                if (isSimilarWord(textWord, termWord)) {
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

    private static boolean isSimilarWord(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        int maxLen = Math.max(a.length(), b.length());
        int threshold = maxLen <= 5 ? 1 : 2;
        return levenshtein(a, b) <= threshold;
    }

    private static int levenshtein(String a, String b) {
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
}
