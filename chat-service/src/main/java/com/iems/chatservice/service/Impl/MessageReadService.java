package com.iems.chatservice.service.Impl;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.ConversationRepository;
import com.iems.chatservice.service.IMessageReadService;
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
public class MessageReadService implements IMessageReadService {

    private final MongoTemplate mongoTemplate;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void markAsRead(String conversationId, String accountId) {
        if (conversationId == null || accountId == null || conversationId.isBlank() || accountId.isBlank()) return;

        Criteria markCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            Criteria.where("senderId").ne(accountId),
            new Criteria().orOperator(
                Criteria.where("readBy").exists(false),
                Criteria.where("readBy").nin(accountId)
            )
        );
        Query q = new Query(markCriteria);
        Update up = new Update().addToSet("readBy", accountId);
        mongoTemplate.updateMulti(q, up, Message.class);
    }

    @Override
    public Map<String, Integer> getUnreadCountsByUser(String accountId) {
        if (accountId == null || accountId.isBlank()) return Map.of();
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(
                        Criteria.where("senderId").ne(accountId),
                        Criteria.where("recalled").ne(true),
                        new Criteria().orOperator(
                                Criteria.where("deletedForUsers").exists(false),
                                Criteria.where("deletedForUsers").nin(accountId)
                        ),
                        new Criteria().orOperator(
                                Criteria.where("readBy").exists(false),
                                Criteria.where("readBy").nin(accountId)
                        )
                )),
                Aggregation.group("conversationId").count().as("count")
        );
        AggregationResults<MessageService.UnreadCountRow> res = mongoTemplate.aggregate(agg, "messages", MessageService.UnreadCountRow.class);
        Map<String, Integer> map = new HashMap<>();
        for (MessageService.UnreadCountRow row : res.getMappedResults()) {
            if (row != null && row.id != null) {
                map.put(row.id, row.count);
            }
        }
        return map;
    }

    @Override
    public int getUnreadCountForConversation(String conversationId, String accountId) {
        if (accountId == null || accountId.isBlank() || conversationId == null || conversationId.isBlank()) {
            return 0;
        }

        Criteria combinedCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            Criteria.where("senderId").ne(accountId),
            Criteria.where("recalled").ne(true),
            new Criteria().orOperator(
                Criteria.where("deletedForUsers").exists(false),
                Criteria.where("deletedForUsers").nin(accountId)
            ),
            new Criteria().orOperator(
                Criteria.where("readBy").exists(false),
                Criteria.where("readBy").nin(accountId)
            )
        );

        Query query = new Query(combinedCriteria);
        return (int) mongoTemplate.count(query, Message.class);
    }

    @Override
    public void markAsReadWithLastMessage(String conversationId, String accountId, String lastMessageId) {
        markAsRead(conversationId, accountId);

        if (lastMessageId != null) {
            Query convQuery = new Query(Criteria.where("id").is(conversationId));
            Update convUpdate = new Update().set("lastReadMessageId." + accountId, lastMessageId);
            mongoTemplate.updateFirst(convQuery, convUpdate, Conversation.class);
        }

        broadcastReadStatusUpdate(conversationId, accountId, lastMessageId);
    }

    @Override
    public boolean markConversationAsRead(String conversationId, String accountId) {
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null || !conversation.getMembers().contains(accountId)) {
            return false;
        }

        Criteria markReadCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            Criteria.where("senderId").ne(accountId),
            new Criteria().orOperator(
                Criteria.where("readBy").exists(false),
                Criteria.where("readBy").nin(accountId)
            )
        );
        Query query = new Query(markReadCriteria);

        Update update = new Update().addToSet("readBy", accountId);
        mongoTemplate.updateMulti(query, update, Message.class);

        Query latestQuery = new Query();
        latestQuery.addCriteria(Criteria.where("conversationId").is(conversationId));
        latestQuery.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        latestQuery.limit(1);

        Message latestMessage = mongoTemplate.findOne(latestQuery, Message.class);
        if (latestMessage != null) {
            Query convQuery = new Query(Criteria.where("id").is(conversationId));
            Update convUpdate = new Update().set("lastReadMessageId." + accountId, latestMessage.getId());
            mongoTemplate.updateFirst(convQuery, convUpdate, Conversation.class);
        }

        // Clear manual unread mark when user reads messages
        if (conversation.getManuallyMarkedAsUnread() != null && conversation.getManuallyMarkedAsUnread().contains(accountId)) {
            Query clearManualQuery = new Query(Criteria.where("id").is(conversationId));
            Update clearManualUpdate = new Update().pull("manuallyMarkedAsUnread", accountId);
            mongoTemplate.updateFirst(clearManualQuery, clearManualUpdate, Conversation.class);
        }

        broadcastReadStatusUpdate(conversationId, accountId, latestMessage != null ? latestMessage.getId() : null);
        return true;
    }

    @Override
    public void broadcastReadStatusUpdate(String conversationId, String accountId, String lastMessageId) {
        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv != null) {
            Map<String, Object> payload = Map.of(
                "event", "messages_read",
                "conversationId", conversationId,
                "accountId", accountId,
                "lastMessageId", lastMessageId != null ? lastMessageId : "",
                "unreadCount", 0
            );

            for (String memberId : conv.getMembers()) {
                messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
            }
        }
    }
}


