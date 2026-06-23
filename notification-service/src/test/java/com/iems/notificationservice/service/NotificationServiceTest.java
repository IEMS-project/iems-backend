package com.iems.notificationservice.service;

import com.iems.notificationservice.dto.CreateNotificationRequest;
import com.iems.notificationservice.dto.NotificationDto;
import com.iems.notificationservice.entity.Notification;
import com.iems.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationService notificationService;

    private CreateNotificationRequest request;
    private UUID recipientId;

    @BeforeEach
    void setUp() {
        recipientId = UUID.randomUUID();
        request = new CreateNotificationRequest();
        request.setRecipientId(recipientId);
        request.setActorId(UUID.randomUUID());
        request.setActorName("Actor");
        request.setType("ISSUE_ASSIGNED");
        request.setTitle("Task 1");
        request.setBody("Body");
        request.setEntityId("issue-1");
        request.setEntityType("ISSUE");
        request.setProjectId(UUID.randomUUID());
        request.setProjectName("Project");
        request.setLinkPath("/projects/1");
        request.setRecipientEmail("user@example.com");
        request.setRecipientName("User");
    }

    @Test
    void createShouldPersistPushAndSendEmail() {
        Notification saved = notificationFromRequest();
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        when(sseEmitterRegistry.push(eq(recipientId), any())).thenReturn(true);

        NotificationDto dto = notificationService.create(request);

        assertEquals(saved.getId(), dto.getId());
        assertFalse(dto.isRead());
        verify(notificationRepository).save(any(Notification.class));
        verify(sseEmitterRegistry).push(eq(recipientId), eq(dto));
        verify(emailService).sendNotificationEmail(request, request.getRecipientEmail(), request.getRecipientName());
    }

    @Test
    void createBatchShouldContinueAfterRepositoryFailure() {
        CreateNotificationRequest bad = new CreateNotificationRequest();
        bad.setRecipientId(UUID.randomUUID());
        bad.setType("MEMBER_ADDED");
        bad.setTitle("Bad");

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if ("Bad".equals(notification.getTitle())) {
                throw new RuntimeException("db boom");
            }
            return notificationFromRequest();
        });
        when(sseEmitterRegistry.push(any(), any())).thenReturn(true);

        notificationService.createBatch(List.of(request, bad));

        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(emailService).sendNotificationEmail(request, request.getRecipientEmail(), request.getRecipientName());
    }

    @Test
    void getNotificationsShouldUseUnreadBranch() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .recipientId(recipientId)
                .type("ISSUE_ASSIGNED")
                .title("Task")
                .read(false)
                .createdAt(Instant.now())
                .build();
        Page<Notification> page = new PageImpl<>(List.of(notification), PageRequest.of(0, 20), 1);
        when(notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(eq(recipientId), eq(PageRequest.of(0, 20))))
                .thenReturn(page);

        Page<NotificationDto> result = notificationService.getNotifications(recipientId, true, 0, 20);

        assertEquals(1, result.getTotalElements());
        assertFalse(result.getContent().get(0).isRead());
        verify(notificationRepository).findByRecipientIdAndReadFalseOrderByCreatedAtDesc(eq(recipientId), eq(PageRequest.of(0, 20)));
    }

    @Test
    void markReadShouldReturnRepositoryResult() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.markRead(notificationId, recipientId)).thenReturn(1);

        boolean updated = notificationService.markRead(notificationId, recipientId);

        assertTrue(updated);
        verify(notificationRepository).markRead(notificationId, recipientId);
    }

    @Test
    void markAllReadShouldReturnRepositoryResult() {
        when(notificationRepository.markAllRead(recipientId)).thenReturn(3);

        int updated = notificationService.markAllRead(recipientId);

        assertEquals(3, updated);
        verify(notificationRepository).markAllRead(recipientId);
    }

    private Notification notificationFromRequest() {
        return Notification.builder()
                .id(UUID.randomUUID())
                .recipientId(request.getRecipientId())
                .actorId(request.getActorId())
                .actorName(request.getActorName())
                .type(request.getType())
                .title(request.getTitle())
                .body(request.getBody())
                .entityId(request.getEntityId())
                .entityType(request.getEntityType())
                .projectId(request.getProjectId())
                .projectName(request.getProjectName())
                .linkPath(request.getLinkPath())
                .read(false)
                .createdAt(Instant.now())
                .build();
    }
}