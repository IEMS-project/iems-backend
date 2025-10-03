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
        }
        return unpinnedMessage;
    }
}


