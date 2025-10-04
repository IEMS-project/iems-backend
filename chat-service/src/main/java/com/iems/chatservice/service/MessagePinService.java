package com.iems.chatservice.service;

import com.iems.chatservice.entity.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessagePinService {

    private final MongoTemplate mongoTemplate;
    private final com.iems.chatservice.repository.MessageRepository messageRepository;
    private final MessageBroadcastService messageBroadcastService;
    private final com.iems.chatservice.client.UserServiceFeignClient userServiceFeignClient;
    private static final String SYSTEM_SENDER = "SYSTEM";

    public Message pinMessage(String conversationId, String messageId, String userId) {
        Query messageQuery = new Query(Criteria.where("id").is(messageId));
        Update messageUpdate = new Update()
                .set("pinned", true)
                .set("pinnedBy", userId)
                .set("pinnedAt", java.time.LocalDateTime.now());
        mongoTemplate.updateFirst(messageQuery, messageUpdate, Message.class);

        Query convQuery = new Query(Criteria.where("id").is(conversationId));
        Update convUpdate = new Update().addToSet("pinnedMessageIds", messageId);
        mongoTemplate.updateFirst(convQuery, convUpdate, com.iems.chatservice.entity.Conversation.class);

        Message pinnedMessage = messageRepository.findById(messageId).orElse(null);
        if (pinnedMessage != null) {
            messageBroadcastService.broadcastMessageUpdate(pinnedMessage, "message_pinned", java.util.Map.of("pinnedBy", userId));

            // create system log
            com.iems.chatservice.entity.Message log = new com.iems.chatservice.entity.Message();
            log.setConversationId(conversationId);
            log.setSenderId(SYSTEM_SENDER);
            log.setType("SYSTEM_LOG");
            log.setContent(String.format("%s đã ghim một tin nhắn", resolveUserName(userId)));
            messageBroadcastService.saveAndBroadcast(log);
        }
        return pinnedMessage;
    }

    public Message unpinMessage(String conversationId, String messageId, String userId) {
        Query messageQuery = new Query(Criteria.where("id").is(messageId));
        Update messageUpdate = new Update()
                .set("pinned", false)
                .unset("pinnedBy")
                .unset("pinnedAt");
        mongoTemplate.updateFirst(messageQuery, messageUpdate, Message.class);

        Query convQuery = new Query(Criteria.where("id").is(conversationId));
        Update convUpdate = new Update().pull("pinnedMessageIds", messageId);
        mongoTemplate.updateFirst(convQuery, convUpdate, com.iems.chatservice.entity.Conversation.class);

        Message unpinnedMessage = messageRepository.findById(messageId).orElse(null);
        if (unpinnedMessage != null) {
            messageBroadcastService.broadcastMessageUpdate(unpinnedMessage, "message_unpinned", java.util.Map.of("unpinnedBy", userId));

            // create system log
            com.iems.chatservice.entity.Message log = new com.iems.chatservice.entity.Message();
            log.setConversationId(conversationId);
            log.setSenderId(SYSTEM_SENDER);
            log.setType("SYSTEM_LOG");
            log.setContent(String.format("%s đã bỏ ghim một tin nhắn", resolveUserName(userId)));
            messageBroadcastService.saveAndBroadcast(log);
        }
        return unpinnedMessage;
    }

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


