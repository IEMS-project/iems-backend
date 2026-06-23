package com.iems.projectservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.CreateIssueDto;
import com.iems.projectservice.dto.response.IssueResponseDto;
import com.iems.projectservice.entity.Issue;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.repository.ActivityLogRepository;
import com.iems.projectservice.repository.AttachmentRepository;
import com.iems.projectservice.repository.IssuePriorityRepository;
import com.iems.projectservice.repository.IssueRepository;
import com.iems.projectservice.repository.IssueStatusHistoryRepository;
import com.iems.projectservice.repository.IssueTypeRepository;
import com.iems.projectservice.repository.LabelRepository;
import com.iems.projectservice.repository.ProjectMemberRepository;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.repository.SprintRepository;
import com.iems.projectservice.repository.WorkflowRepository;
import com.iems.projectservice.repository.WorkflowStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private IssueTypeRepository issueTypeRepository;
    @Mock
    private IssuePriorityRepository issuePriorityRepository;
    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private WorkflowStatusRepository workflowStatusRepository;
    @Mock
    private IssueStatusHistoryRepository issueStatusHistoryRepository;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private UserServiceFeignClient userServiceFeignClient;
    @Mock
    private ProjectSubscriptionSyncService projectSubscriptionSyncService;
    @Mock
    private SubscriptionLimitService subscriptionLimitService;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private ActorNameResolver actorNameResolver;
    @Mock
    private LabelRepository labelRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private AttachmentService attachmentService;

    private IssueService service;

    @BeforeEach
    void setUp() {
        service = new IssueService(
                issueRepository,
                projectRepository,
                projectMemberRepository,
                sprintRepository,
                issueTypeRepository,
                issuePriorityRepository,
                workflowRepository,
                workflowStatusRepository,
                issueStatusHistoryRepository,
                activityLogService,
                userServiceFeignClient,
                new ObjectMapper(),
                projectSubscriptionSyncService,
                subscriptionLimitService,
                notificationPublisher,
                actorNameResolver,
                labelRepository,
                attachmentRepository,
                attachmentService);
    }

    @Test
    void createIssueShouldUseNextNumberAfterCurrentMaxIssueKey() {
        UUID projectId = UUID.randomUUID();
        UUID issueTypeId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);
        project.setProjectKey("ABC");
        project.setOwnerSubscription("FREE");

        CreateIssueDto dto = new CreateIssueDto();
        dto.setIssueTypeId(issueTypeId);
        dto.setTitle("New task");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectSubscriptionSyncService.refreshProjectSubscription(project)).thenReturn(project);
        when(issueRepository.countByProjectId(projectId)).thenReturn(2L);
        when(issueRepository.findMaxIssueNumberByProjectIdAndProjectKey(projectId, "ABC")).thenReturn(3);
        when(workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)).thenReturn(Optional.empty());
        when(issueRepository.findMaxSortOrderByProjectId(projectId)).thenReturn(Optional.of(3));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> {
            Issue issue = invocation.getArgument(0);
            issue.setId(UUID.randomUUID());
            issue.setLabels(new HashSet<>());
            return issue;
        });
        when(attachmentRepository.findByIssueId(any(UUID.class))).thenReturn(List.of());

        IssueResponseDto result = service.createIssue(projectId, dto, null);

        assertEquals("ABC-4", result.getIssueKey());
        ArgumentCaptor<Issue> issueCaptor = ArgumentCaptor.forClass(Issue.class);
        verify(issueRepository).save(issueCaptor.capture());
        assertEquals("ABC-4", issueCaptor.getValue().getIssueKey());
    }
}
