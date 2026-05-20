package com.iems.projectservice.service;

import com.iems.projectservice.entity.Issue;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.repository.IssueRepository;
import com.iems.projectservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scheduler chạy mỗi ngày lúc 08:00 để kiểm tra các task
 * có dueDate = ngày mai và gửi thông báo (in-app + email) cho assignee.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssueDueDateReminderScheduler {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final NotificationPublisher notificationPublisher;
    private final UserEmailResolver userEmailResolver;

    /**
     * Chạy hàng ngày lúc 08:00 sáng (giờ server).
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDueTomorrowReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        log.info("[DueDateReminder] Checking issues due on {} ...", tomorrow);

        // Lấy tất cả issues có dueDate = ngày mai và có assignee
        List<Issue> dueIssues = issueRepository.findByDueDateAndAssigneeIdNotNull(tomorrow);


        if (dueIssues.isEmpty()) {
            log.info("[DueDateReminder] No issues due tomorrow.");
            return;
        }

        log.info("[DueDateReminder] Found {} issue(s) due tomorrow.", dueIssues.size());

        // Batch resolve emails cho tất cả assignees
        Set<UUID> assigneeIds = dueIssues.stream()
                .map(Issue::getAssigneeId)
                .collect(Collectors.toSet());
        Map<UUID, UserEmailResolver.UserInfo> emailMap = userEmailResolver.resolveAll(assigneeIds);

        // Batch load project names
        Set<UUID> projectIds = dueIssues.stream()
                .map(Issue::getProjectId)
                .collect(Collectors.toSet());
        Map<UUID, String> projectNameMap = projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a));

        int sent = 0;
        for (Issue issue : dueIssues) {
            try {
                UUID assigneeId = issue.getAssigneeId();
                UserEmailResolver.UserInfo info = emailMap.getOrDefault(assigneeId,
                        new UserEmailResolver.UserInfo(null, null));
                String projectName = projectNameMap.getOrDefault(issue.getProjectId(), "");

                notificationPublisher.notifyIssueDueSoon(
                        assigneeId,
                        issue.getIssueKey(),
                        issue.getTitle(),
                        issue.getId(),
                        issue.getProjectId(),
                        projectName,
                        info.email(),
                        info.displayName()
                );
                sent++;
            } catch (Exception e) {
                log.warn("[DueDateReminder] Failed to notify for issue {}: {}", issue.getIssueKey(), e.getMessage());
            }
        }

        log.info("[DueDateReminder] Done. Sent {} reminder(s).", sent);
    }
}
