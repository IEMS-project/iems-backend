package com.iems.chatservice.service;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.ConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ConversationService {
    
    @Autowired
    private ConversationRepository conversationRepository;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Autowired
    private MessageService messageService;

    public List<Map<String, Object>> getConversationsByUser(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("members").in(userId));
        query.with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<Conversation> conversations = mongoTemplate.find(query, Conversation.class);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Conversation conv : conversations) {
            Map<String, Object> convData = new HashMap<>();
            convData.put("id", conv.getId());
            convData.put("name", conv.getName());
            convData.put("type", conv.getType());
            convData.put("members", conv.getMembers());
            convData.put("createdAt", conv.getCreatedAt());
            convData.put("updatedAt", conv.getUpdatedAt());
            convData.put("pinnedMessageIds", conv.getPinnedMessageIds());
            
            // Get last message
            Message lastMessage = getLastMessageForConversation(conv.getId(), userId);
            if (lastMessage != null) {
                Map<String, Object> lastMsgData = new HashMap<>();
                lastMsgData.put("id", lastMessage.getId());
                lastMsgData.put("content", lastMessage.getContent());
                lastMsgData.put("senderId", lastMessage.getSenderId());
                lastMsgData.put("sentAt", lastMessage.getSentAt());
                lastMsgData.put("type", lastMessage.getType());
                convData.put("lastMessage", lastMsgData);
            }
            
            // Get unread count for this user
            int unreadCount = messageService.getUnreadCountForConversation(conv.getId(), userId);
            convData.put("unreadCount", unreadCount);
            
            result.add(convData);
        }
        
        return result;
    }
    
    private Message getLastMessageForConversation(String conversationId, String userId) {
        Criteria lastMessageCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            new Criteria().orOperator(
                Criteria.where("deletedForUsers").exists(false),
                Criteria.where("deletedForUsers").nin(userId)
            ) // Exclude messages deleted by this user
        );
        
        Query query = new Query(lastMessageCriteria);
        query.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        query.limit(1);
        
        Message lastMessage = mongoTemplate.findOne(query, Message.class);
        
        // If the last message is recalled, we need to find the next non-recalled message
        if (lastMessage != null && lastMessage.isRecalled()) {
            // Find the most recent non-recalled message
            Criteria nonRecalledCriteria = new Criteria().andOperator(
                Criteria.where("conversationId").is(conversationId),
                Criteria.where("recalled").ne(true),
                new Criteria().orOperator(
                    Criteria.where("deletedForUsers").exists(false),
                    Criteria.where("deletedForUsers").nin(userId)
                )
            );
            
            Query nonRecalledQuery = new Query(nonRecalledCriteria);
            nonRecalledQuery.with(Sort.by(Sort.Direction.DESC, "sentAt"));
            nonRecalledQuery.limit(1);
            
            Message nonRecalledMessage = mongoTemplate.findOne(nonRecalledQuery, Message.class);
            if (nonRecalledMessage != null) {
                return nonRecalledMessage;
            }
            
            // If no non-recalled message found, return a special "recalled" message
            Message recalledMessage = new Message();
            recalledMessage.setId("recalled");
            recalledMessage.setContent("Tin nhắn đã được thu hồi");
            recalledMessage.setSenderId(lastMessage.getSenderId());
            recalledMessage.setSentAt(lastMessage.getSentAt());
            recalledMessage.setType("SYSTEM");
            recalledMessage.setRecalled(true);
            return recalledMessage;
        }
        
        return lastMessage;
    }
    
    public Conversation findById(String conversationId) {
        return conversationRepository.findById(conversationId).orElse(null);
    }
    
    public Conversation save(Conversation conversation) {
        return conversationRepository.save(conversation);
    }
    
    public List<Conversation> findByMembersContaining(String userId) {
        return conversationRepository.findByMembersContaining(userId);
    }
}
