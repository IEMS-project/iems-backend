package com.iems.chatservice.service.Impl;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.ConversationRepository;
import com.iems.chatservice.repository.MessageRepository;
import com.iems.chatservice.service.IMessageBroadcastService;
import com.iems.chatservice.service.IMessageDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageDeletionService implements IMessageDeletionService {

    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private IMessageBroadcastService messageBroadcastService;

    @Override
    public boolean deleteForMe(String messageId, String userId) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) {
            return false;
        }

        Conversation conversation = conversationRepository.findById(message.getConversationId()).orElse(null);
        if (conversation == null || !conversation.getMembers().contains(userId)) {
            return false;
        }

        if (message.getDeletedForUsers() != null && message.getDeletedForUsers().contains(userId)) {
            return true;
        }

        Query query = new Query(Criteria.where("id").is(messageId));
        Update update = new Update().addToSet("deletedForUsers", userId);
        mongoTemplate.updateFirst(query, update, Message.class);

        try {
            Map<String, Object> payload = Map.of(
                "event", "message_deleted_for_user",
                "messageId", messageId,
                "userId", userId,
                "conversationId", message.getConversationId()
            );

            for (String memberId : conversation.getMembers()) {
                messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
            }

            Message latestVisible = messageBroadcastService.getLatestVisibleMessageForUser(message.getConversationId(), userId);
            Map<String, Object> convUpdate = new HashMap<>();
            convUpdate.put("event", "conversation_updated");
            convUpdate.put("conversationId", message.getConversationId());
            if (latestVisible != null) {
                String lmContent = (Boolean.TRUE.equals(latestVisible.isRecalled())) ?
                        "Tin nhắn đã được thu hồi" : latestVisible.getContent();
                convUpdate.put("lastMessage", Map.of(
                        "id", latestVisible.getId(),
                        "content", lmContent,
                        "senderId", latestVisible.getSenderId(),
                        "sentAt", latestVisible.getSentAt(),
                        "type", latestVisible.getType()
                ));
                convUpdate.put("updatedAt", latestVisible.getSentAt());
            }
            messagingTemplate.convertAndSend("/topic/user-updates/" + userId, convUpdate);
        } catch (Exception e) {
            System.err.println("Failed to broadcast delete event: " + e.getMessage());
        }

        return true;
    }

    @Override
    public Message recallMessage(String messageId, String userId) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return null;

        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("Only sender can recall message");
        }

        message.setRecalled(true);
        message.setRecalledAt(LocalDateTime.now());
        Message saved = messageRepository.save(message);

        messageBroadcastService.broadcastMessageUpdate(saved, "message_recalled", Map.of("recalledBy", userId));

        Conversation conv = conversationRepository.findById(saved.getConversationId()).orElse(null);
        if (conv != null) {
            messageBroadcastService.broadcastConversationUpdate(conv, saved);
        }

        return saved;
    }
}


