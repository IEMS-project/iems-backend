package com.iems.notificationservice.service;

import com.iems.notificationservice.dto.CreateNotificationRequest;
import com.iems.notificationservice.dto.NotificationDto;
import com.iems.notificationservice.entity.Notification;
import com.iems.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final EmailService emailService;

    // ── Create ────────────────────────────────────────────────────

    /**
     * Creates notification data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param req the req parameter
     * @return the create result
     */
    @Transactional
    public NotificationDto create(CreateNotificationRequest req) {
        Notification notification = Notification.builder()
                .recipientId(req.getRecipientId())
                .actorId(req.getActorId())
                .actorName(req.getActorName())
                .type(req.getType())
                .title(req.getTitle())
                .body(req.getBody())
                .entityId(req.getEntityId())
                .entityType(req.getEntityType())
                .projectId(req.getProjectId())
                .projectName(req.getProjectName())
                .linkPath(req.getLinkPath())
                .read(false)
                .build();

        notification = notificationRepository.save(notification);
        NotificationDto dto = toDto(notification);

        // Push realtime via SSE (fire-and-forget)
        boolean pushed = sseEmitterRegistry.push(req.getRecipientId(), dto);
        log.debug("Notification created for user {}, pushed via SSE: {}", req.getRecipientId(), pushed);

        // Send email (fire-and-forget async)
        emailService.sendNotificationEmail(req, req.getRecipientEmail(), req.getRecipientName());

        return dto;
    }

    /**
     * Creates notification data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param requests the requests parameter
     */
    @Transactional
    public void createBatch(List<CreateNotificationRequest> requests) {
        for (CreateNotificationRequest req : requests) {
            try {
                create(req);
            } catch (Exception e) {
                log.warn("Failed to create notification for recipient {}: {}", req.getRecipientId(), e.getMessage());
            }
        }
    }

    // ── Read ──────────────────────────────────────────────────────

    /**
     * Retrieves notification information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @param unreadOnly the unread only parameter
     * @param page the page parameter
     * @param size the size parameter
     * @return the paginated result set
     */
    public Page<NotificationDto> getNotifications(UUID userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
        return result.map(this::toDto);
    }

    /**
     * Counts notification records.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @return the count unread result
     */
    public long countUnread(UUID userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    // ── Update ────────────────────────────────────────────────────

    /**
     * Marks notification data according to the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param notificationId the notification id parameter
     * @param userId the user id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    @Transactional
    public boolean markRead(UUID notificationId, UUID userId) {
        return notificationRepository.markRead(notificationId, userId) > 0;
    }

    /**
     * Marks notification data according to the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @return the mark all read result
     */
    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId);
    }

    // ── Mapper ────────────────────────────────────────────────────

    /**
     * Returns to dto for notification processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param n the n parameter
     * @return the to dto result
     */
    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .recipientId(n.getRecipientId())
                .actorId(n.getActorId())
                .actorName(n.getActorName())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .entityId(n.getEntityId())
                .entityType(n.getEntityType())
                .projectId(n.getProjectId())
                .projectName(n.getProjectName())
                .linkPath(n.getLinkPath())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
