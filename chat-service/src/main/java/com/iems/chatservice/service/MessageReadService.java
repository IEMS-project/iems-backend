package com.iems.chatservice.service;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageReadService {

    private final MongoTemplate mongoTemplate;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void markAsRead(String conversationId, String userId) {
        if (conversationId == null || userId == null || conversationId.isBlank() || userId.isBlank()) return;

        Criteria markCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            Criteria.where("senderId").ne(userId),
            new Criteria().orOperator(
                Criteria.where("readBy").exists(false),
                Criteria.where("readBy").nin(userId)
            )
        );
        Query q = new Query(markCriteria);
        Update up = new Update().addToSet("readBy", userId);
        mongoTemplate.updateMulti(q, up, Message.class);
    }

    public Map<String, Integer> getUnreadCountsByUser(String userId) {
        if (userId == null || userId.isBlank()) return Map.of();
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(
                        Criteria.where("senderId").ne(userId),
                        Criteria.where("recalled").ne(true),
                        new Criteria().orOperator(
                                Criteria.where("deletedForUsers").exists(false),
                                Criteria.where("deletedForUsers").nin(userId)
                        ),
                        new Criteria().orOperator(
                                Criteria.where("readBy").exists(false),
                                Criteria.where("readBy").nin(userId)
                        )
                )),
                Aggregation.group("conversationId").count().as("count")
        );
        AggregationResults<com.iems.chatservice.service.MessageService.UnreadCountRow> res = mongoTemplate.aggregate(agg, "messages", com.iems.chatservice.service.MessageService.UnreadCountRow.class);
        Map<String, Integer> map = new HashMap<>();
        for (com.iems.chatservice.service.MessageService.UnreadCountRow row : res.getMappedResults()) {
            if (row != null && row.id != null) {
                map.put(row.id, row.count);
            }
        }
        return map;
    }

    public int getUnreadCountForConversation(String conversationId, String userId) {
        if (userId == null || userId.isBlank() || conversationId == null || conversationId.isBlank()) {
            return 0;
        }

        Criteria combinedCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            Criteria.where("senderId").ne(userId),
            Criteria.where("recalled").ne(true),
            new Criteria().orOperator(
                Criteria.where("deletedForUsers").exists(false),
                Criteria.where("deletedForUsers").nin(userId)
            ),
            new Criteria().orOperator(
                Criteria.where("readBy").exists(false),
                Criteria.where("readBy").nin(userId)
            )
        );

        Query query = new Query(combinedCriteria);
        return (int) mongoTemplate.count(query, Message.class);
    }

    public void markAsReadWithLastMessage(String conversationId, String userId, String lastMessageId) {
        markAsRead(conversationId, userId);

        if (lastMessageId != null) {
            Query convQuery = new Query(Criteria.where("id").is(conversationId));
            Update convUpdate = new Update().set("lastReadMessageId." + userId, lastMessageId);
            mongoTemplate.updateFirst(convQuery, convUpdate, Conversation.class);
        }

        broadcastReadStatusUpdate(conversationId, userId, lastMessageId);
    }

    public boolean markConversationAsRead(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null || !conversation.getMembers().contains(userId)) {
            return false;
        }

        Criteria markReadCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            Criteria.where("senderId").ne(userId),
            new Criteria().orOperator(
                Criteria.where("readBy").exists(false),
                Criteria.where("readBy").nin(userId)
            )
        );
        Query query = new Query(markReadCriteria);

        Update update = new Update().addToSet("readBy", userId);
        mongoTemplate.updateMulti(query, update, Message.class);

        Query latestQuery = new Query();
        latestQuery.addCriteria(Criteria.where("conversationId").is(conversationId));
        latestQuery.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        latestQuery.limit(1);

        Message latestMessage = mongoTemplate.findOne(latestQuery, Message.class);
        if (latestMessage != null) {
            Query convQuery = new Query(Criteria.where("id").is(conversationId));
            Update convUpdate = new Update().set("lastReadMessageId." + userId, latestMessage.getId());
            mongoTemplate.updateFirst(convQuery, convUpdate, Conversation.class);
        }

        // Clear manual unread mark when user reads messages
        if (conversation.getManuallyMarkedAsUnread() != null && conversation.getManuallyMarkedAsUnread().contains(userId)) {
            Query clearManualQuery = new Query(Criteria.where("id").is(conversationId));
            Update clearManualUpdate = new Update().pull("manuallyMarkedAsUnread", userId);
            mongoTemplate.updateFirst(clearManualQuery, clearManualUpdate, Conversation.class);
        }

        broadcastReadStatusUpdate(conversationId, userId, latestMessage != null ? latestMessage.getId() : null);
        return true;
    }

    private void broadcastReadStatusUpdate(String conversationId, String userId, String lastMessageId) {
        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv != null) {
            Map<String, Object> payload = Map.of(
                "event", "messages_read",
                "conversationId", conversationId,
                "userId", userId,
                "lastMessageId", lastMessageId != null ? lastMessageId : "",
                "unreadCount", 0
            );

            for (String memberId : conv.getMembers()) {
                messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
            }
        }
    }
}


