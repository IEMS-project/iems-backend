package com.iems.chatservice.service;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.ConversationRepository;
import com.iems.chatservice.client.UserServiceFeignClient;
import com.iems.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GroupMemberService {

    private static final String SYSTEM_SENDER = "SYSTEM";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageBroadcastService messageBroadcastService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserServiceFeignClient userServiceFeignClient;

    public Conversation addMember(String conversationId, String userId, String actorUserId) {
        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv == null) return null;
        List<String> members = conv.getMembers();
        if (members == null) {
            members = new ArrayList<>();
            conv.setMembers(members);
        }
        if (!members.contains(userId)) {
            members.add(userId);
        }
        Conversation updated = conversationRepository.save(conv);

        // Create system log message
        String content = String.format("%s đã tham gia nhóm", resolveUserName(userId));
        createAndBroadcastSystemLog(updated.getId(), content);

        // Broadcast member-added event to all participants
        broadcastMemberEvent(updated, "member_added", userId, actorUserId);
        return updated;
    }

    public Conversation removeMember(String conversationId, String userId, String actorUserId) {
        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv == null) return null;
        try {
            String creatorId = conv.getCreatedBy();
            boolean isSelfRemoval = actorUserId != null && actorUserId.equals(userId);
            boolean isCreator = actorUserId != null && actorUserId.equals(creatorId);
            if (!isSelfRemoval && !isCreator) {
                // Not allowed
                return null;
            }
        } catch (Exception ignored) { }
        List<String> members = conv.getMembers();
        if (members != null) {
            members.remove(userId);
        }
        Conversation updated = conversationRepository.save(conv);

        String content = String.format("%s đã rời nhóm", resolveUserName(userId));
        createAndBroadcastSystemLog(updated.getId(), content);
        broadcastMemberEvent(updated, "member_removed", userId, actorUserId);
        return updated;
    }

    private void createAndBroadcastSystemLog(String conversationId, String content) {
        Message log = new Message();
        log.setConversationId(conversationId);
        log.setSenderId(SYSTEM_SENDER);
        log.setType("SYSTEM_LOG");
        log.setContent(content);
        log.setSentAt(LocalDateTime.now());
        messageBroadcastService.saveAndBroadcast(log);
    }

    private void broadcastMemberEvent(Conversation conversation, String event, String targetUserId, String actorUserId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", event);
            payload.put("conversationId", conversation.getId());
            payload.put("targetUserId", targetUserId);
            payload.put("actorUserId", actorUserId);
            payload.put("updatedAt", LocalDateTime.now());

            // broadcast to conversation room
            messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), payload);
            // and to all members' updates channel
            List<String> members = conversation.getMembers();
            if (members != null) {
                for (String memberId : members) {
                    messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
                }
            }
        } catch (Exception ignore) { }
    }

    // Placeholder for resolving display name (can be enhanced to call user service)
    private String resolveUserName(String userId) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(userId);
            var resp = userServiceFeignClient.getUserById(uuid);
            var body = resp.getBody();
            if (body != null && body.containsKey("data")) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> data = (java.util.Map<String, Object>) body.get("data");
                String firstName = data.getOrDefault("firstName", "").toString();
                String lastName = data.getOrDefault("lastName", "").toString();
                String email = data.getOrDefault("email", "").toString();
                String full = (firstName + " " + lastName).trim();
                return full.isBlank() ? (email.isBlank() ? userId : email) : full;
            }
        } catch (Exception ignored) { }
        return userId;
    }
}


