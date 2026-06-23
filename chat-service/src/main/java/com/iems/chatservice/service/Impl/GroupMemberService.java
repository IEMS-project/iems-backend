package com.iems.chatservice.service.Impl;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.ConversationRepository;
import com.iems.chatservice.repository.MessageRepository;
import com.iems.chatservice.service.IGroupMemberService;
import com.iems.chatservice.service.IMessageBroadcastService;
import com.iems.chatservice.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GroupMemberService implements IGroupMemberService {

    private static final String SYSTEM_SENDER = "SYSTEM";

    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private IMessageBroadcastService messageBroadcastService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;


    @Autowired
    private IUserService userService;

    @Override
    public Conversation addMember(String conversationId, String accountId) {
        UUID actorAccountId = userService.getAccountIdFromRequest();
        String actorAccountIdStr = actorAccountId.toString();
        
        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv == null) return null;
        List<String> members = conv.getMembers();
        if (members == null) {
            members = new ArrayList<>();
            conv.setMembers(members);
        }
        if (!members.contains(accountId)) {
            members.add(accountId);
        }
        Conversation updated = conversationRepository.save(conv);

        // Create system log message
        String content = String.format("%s đã tham gia nhóm", userService.resolveUserName(accountId));
        createAndBroadcastSystemLog(updated.getId(), content);

        // Broadcast member-added event to all participants
        broadcastMemberEvent(updated, "member_added", accountId, actorAccountIdStr);
        return updated;
    }

    @Override
    public Conversation removeMember(String conversationId, String accountId) {
        UUID actorAccountId = userService.getAccountIdFromRequest();
        String actorAccountIdStr = actorAccountId.toString();
        
        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv == null) return null;
        try {
            String creatorId = conv.getCreatedBy();
            boolean isSelfRemoval = actorAccountIdStr.equals(accountId);
            boolean isCreator = actorAccountIdStr.equals(creatorId);
            if (!isSelfRemoval && !isCreator) {
                // Not allowed
                return null;
            }
        } catch (Exception ignored) { }
        List<String> members = conv.getMembers();
        if (members != null) {
            members.remove(accountId);
        }
        Conversation updated = conversationRepository.save(conv);

        String content = String.format("%s đã rời nhóm", userService.resolveUserName(accountId));
        createAndBroadcastSystemLog(updated.getId(), content);
        broadcastMemberEvent(updated, "member_removed", accountId, actorAccountIdStr);
        return updated;
    }

    @Override
    public void createAndBroadcastSystemLog(String conversationId, String content) {
        Message log = new Message();
        log.setConversationId(conversationId);
        log.setSenderId(SYSTEM_SENDER);
        log.setType("SYSTEM_LOG");
        log.setContent(content);
        log.setSentAt(LocalDateTime.now());
        messageBroadcastService.saveAndBroadcast(log);
    }

    @Override
    public void broadcastMemberEvent(Conversation conversation, String event, String targetAccountId, String actorAccountId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", event);
            payload.put("conversationId", conversation.getId());
            payload.put("targetAccountId", targetAccountId);
            payload.put("actorAccountId", actorAccountId);
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
}


