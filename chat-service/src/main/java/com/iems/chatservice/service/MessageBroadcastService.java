package com.iems.chatservice.service;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.ConversationRepository;
import com.iems.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageBroadcastService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MongoTemplate mongoTemplate;

    public Message saveAndBroadcast(Message message) {
        Message saved = messageRepository.save(message);

        Conversation conversation = conversationRepository.findById(saved.getConversationId()).orElse(null);
        if (conversation != null) {
            messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), saved);

            if (!"GROUP".equalsIgnoreCase(conversation.getType())) {
                List<String> members = conversation.getMembers();
                for (String memberId : members) {
                    messagingTemplate.convertAndSendToUser(memberId, "/queue/messages", saved);
                }
            }

            try {
                List<String> members = conversation.getMembers();
                if (members != null) {
                    for (String memberId : members) {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("event", "message");
                        payload.put("conversationId", conversation.getId());
                        payload.put("senderId", saved.getSenderId());
                        payload.put("content", saved.getContent());
                        payload.put("type", saved.getType());
                        payload.put("messageId", saved.getId());
                        payload.put("timestamp", saved.getSentAt().toString());

                        messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
                    }

                    broadcastConversationUpdate(conversation, saved);
                }
            } catch (Exception ignore) { }
        }
        return saved;
    }

    public void broadcastMessageUpdate(Message message, String eventType, Map<String, Object> additionalData) {
        Conversation conversation = conversationRepository.findById(message.getConversationId()).orElse(null);
        if (conversation != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", eventType);
            payload.put("messageId", message.getId());
            payload.put("conversationId", message.getConversationId());
            payload.put("message", message);
            payload.putAll(additionalData);

            messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), payload);

            for (String memberId : conversation.getMembers()) {
                messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
            }
        }
    }

    public void broadcastConversationUpdate(Conversation conversation, Message lastMessage) {
        try {
            List<String> members = conversation.getMembers();
            if (members != null) {
                for (String memberId : members) {
                    Map<String, Object> conversationUpdate = new HashMap<>();
                    conversationUpdate.put("event", "conversation_updated");
                    conversationUpdate.put("conversationId", conversation.getId());
                    if (lastMessage != null) {
                        String content = (Boolean.TRUE.equals(lastMessage.isRecalled()))
                                ? "Tin nhắn đã được thu hồi"
                                : lastMessage.getContent();
                        conversationUpdate.put("lastMessage", Map.of(
                                "id", lastMessage.getId(),
                                "content", content,
                                "senderId", lastMessage.getSenderId(),
                                "sentAt", lastMessage.getSentAt(),
                                "type", lastMessage.getType()
                        ));
                        conversationUpdate.put("updatedAt", lastMessage.getSentAt());
                    }

                    messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, conversationUpdate);
                }
            }
        } catch (Exception e) {
            System.err.println("Error broadcasting conversation update: " + e.getMessage());
        }
    }

    public void broadcastConversationMetaUpdate(Conversation conversation, java.util.Map<String, Object> changedFields) {
        try {
            java.util.List<String> members = conversation.getMembers();
            if (members != null) {
                for (String memberId : members) {
                    java.util.Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("event", "conversation_meta_updated");
                    payload.put("conversationId", conversation.getId());
                    if (changedFields != null) {
                        payload.putAll(changedFields);
                    }
                    messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
                }
            }
        } catch (Exception e) {
            System.err.println("Error broadcasting conversation meta update: " + e.getMessage());
        }
    }

    public Message getLatestVisibleMessageForUser(String conversationId, String userId) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("conversationId").is(conversationId),
                Criteria.where("recalled").ne(true),
                new Criteria().orOperator(
                        Criteria.where("deletedForUsers").exists(false),
                        Criteria.where("deletedForUsers").nin(userId)
                )
        );
        Query query = new Query(criteria);
        query.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        query.limit(1);
        return mongoTemplate.findOne(query, Message.class);
    }
}


