package com.iems.chatservice.service.Impl;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.ConversationRepository;
import com.iems.chatservice.service.IConversationService;
import com.iems.chatservice.service.IMessageService;
import com.iems.chatservice.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ConversationService implements IConversationService {
    
    @Autowired
    private ConversationRepository conversationRepository;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Autowired
    private IMessageService messageService;

    @Autowired
    private IUserService userService;


    /**
     * Retrieves conversation information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @return the get conversations by user result
     */
    public List<Map<String, Object>> getConversationsByUser() {
        UUID accountId = userService.getAccountIdFromRequest();
        String accountIdStr = accountId.toString();
        
        Query query = new Query();
        query.addCriteria(Criteria.where("members").in(accountIdStr));
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
            convData.put("createdBy", conv.getCreatedBy());
            convData.put("pinnedMessageIds", conv.getPinnedMessageIds());
            // expose avatar url if any (used by frontend for group avatar)
            convData.put("avatarUrl", conv.getAvatarUrl());
            
            // Check if this conversation is pinned by the user
            boolean isPinned = conv.getPinnedBy() != null && conv.getPinnedBy().containsKey(accountIdStr);
            convData.put("isPinned", isPinned);
            if (isPinned) {
                convData.put("pinnedAt", conv.getPinnedBy().get(accountIdStr));
            }
            
            // Check notification settings for this user
            boolean notificationsEnabled = conv.getNotificationSettings() == null || 
                conv.getNotificationSettings().getOrDefault(accountIdStr, true);
            convData.put("notificationsEnabled", notificationsEnabled);
            
            // Check if manually marked as unread by this user
            boolean manuallyMarkedAsUnread = conv.getManuallyMarkedAsUnread() != null && 
                conv.getManuallyMarkedAsUnread().contains(accountIdStr);
            convData.put("manuallyMarkedAsUnread", manuallyMarkedAsUnread);
            
            // Get last message
            Message lastMessage = getLastMessageForConversation(conv.getId(), accountIdStr);
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
            int unreadCount = messageService.getUnreadCountForConversation(conv.getId());
            
            // Apply manual unread logic: if manually marked as unread, show 1; otherwise show actual unread count
            int displayUnreadCount;
            if (manuallyMarkedAsUnread) {
                displayUnreadCount = 1;
            } else {
                displayUnreadCount = unreadCount;
            }
            convData.put("unreadCount", displayUnreadCount);
            convData.put("actualUnreadCount", unreadCount); // Keep actual count for reference
            
            result.add(convData);
        }
        
        // Sort conversations: pinned first (by pinnedAt desc), then by last message time desc
        result.sort((a, b) -> {
            boolean aPinned = (Boolean) a.getOrDefault("isPinned", false);
            boolean bPinned = (Boolean) b.getOrDefault("isPinned", false);
            
            if (aPinned && !bPinned) return -1;
            if (!aPinned && bPinned) return 1;
            
            if (aPinned && bPinned) {
                // Both pinned, sort by pinnedAt desc
                LocalDateTime aPinnedAt = (LocalDateTime) a.get("pinnedAt");
                LocalDateTime bPinnedAt = (LocalDateTime) b.get("pinnedAt");
                if (aPinnedAt != null && bPinnedAt != null) {
                    return bPinnedAt.compareTo(aPinnedAt);
                }
            }
            
            // Both not pinned or same pinned status, sort by last message time
            @SuppressWarnings("unchecked")
            Map<String, Object> aLastMsg = (Map<String, Object>) a.get("lastMessage");
            @SuppressWarnings("unchecked")
            Map<String, Object> bLastMsg = (Map<String, Object>) b.get("lastMessage");
            
            if (aLastMsg != null && bLastMsg != null) {
                LocalDateTime aTime = (LocalDateTime) aLastMsg.get("sentAt");
                LocalDateTime bTime = (LocalDateTime) bLastMsg.get("sentAt");
                if (aTime != null && bTime != null) {
                    return bTime.compareTo(aTime);
                }
            }
            
            // Fallback to updatedAt
            LocalDateTime aUpdated = (LocalDateTime) a.get("updatedAt");
            LocalDateTime bUpdated = (LocalDateTime) b.get("updatedAt");
            if (aUpdated != null && bUpdated != null) {
                return bUpdated.compareTo(aUpdated);
            }
            
            return 0;
        });
        
        return result;
    }

    /**
     * Retrieves conversation information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     * @return the get last message for conversation result
     */
    @Override
    public Message getLastMessageForConversation(String conversationId, String accountId) {
        Criteria lastMessageCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            new Criteria().orOperator(
                Criteria.where("deletedForUsers").exists(false),
                Criteria.where("deletedForUsers").nin(accountId)
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
                    Criteria.where("deletedForUsers").nin(accountId)
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
    
    /**
     * Finds conversation information that matches the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @return the find by id result
     */
    @Override
    public Conversation findById(String conversationId) {
        return conversationRepository.findById(conversationId).orElse(null);
    }
    
    /**
     * Saves conversation data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param conversation the conversation parameter
     * @return the save result
     */
    @Override
    public Conversation save(Conversation conversation) {
        return conversationRepository.save(conversation);
    }
    
    /**
     * Finds conversation information that matches the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param accountId the account id parameter
     * @return the matching result collection
     */
    @Override
    public List<Conversation> findByMembersContaining(String accountId) {
        return conversationRepository.findByMembersContaining(accountId);
    }
    
    /**
     * Pins conversation data for quick access.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    @Override
    public boolean pinConversation(String conversationId) {
        UUID accountId = userService.getAccountIdFromRequest();
        String accountIdStr = accountId.toString();
        
        try {
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) {
                return false;
            }
            
            // Check if user is a member of the conversation
            if (!conversation.getMembers().contains(accountIdStr)) {
                return false;
            }
            
            // Initialize pinnedBy map if null
            if (conversation.getPinnedBy() == null) {
                conversation.setPinnedBy(new HashMap<>());
            }
            
            // Add user to pinnedBy map with current timestamp
            conversation.getPinnedBy().put(accountIdStr, LocalDateTime.now());
            conversation.setUpdatedAt(LocalDateTime.now());
            
            conversationRepository.save(conversation);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Unpins conversation data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    @Override
    public boolean unpinConversation(String conversationId) {
        UUID accountId = userService.getAccountIdFromRequest();
        String accountIdStr = accountId.toString();
        
        try {
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) {
                return false;
            }
            
            // Check if user is a member of the conversation
            if (!conversation.getMembers().contains(accountIdStr)) {
                return false;
            }
            
            // Remove user from pinnedBy map
            if (conversation.getPinnedBy() != null) {
                conversation.getPinnedBy().remove(accountIdStr);
                conversation.setUpdatedAt(LocalDateTime.now());
                conversationRepository.save(conversation);
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Marks conversation data according to the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    @Override
    public boolean markConversationAsUnread(String conversationId) {
        UUID accountId = userService.getAccountIdFromRequest();
        String accountIdStr = accountId.toString();
        
        try {
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) {
                return false;
            }
            
            // Check if user is a member of the conversation
            if (!conversation.getMembers().contains(accountIdStr)) {
                return false;
            }
            
            // Initialize manuallyMarkedAsUnread set if null
            if (conversation.getManuallyMarkedAsUnread() == null) {
                conversation.setManuallyMarkedAsUnread(new HashSet<>());
            }
            
            // Add user to manually marked as unread set
            conversation.getManuallyMarkedAsUnread().add(accountIdStr);
            conversation.setUpdatedAt(LocalDateTime.now());
            
            conversationRepository.save(conversation);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Returns toggle notification settings for conversation processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    @Override
    public boolean toggleNotificationSettings(String conversationId) {
        UUID accountId = userService.getAccountIdFromRequest();
        String accountIdStr = accountId.toString();
        
        try {
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) {
                return false;
            }
            
            // Check if user is a member of the conversation
            if (!conversation.getMembers().contains(accountIdStr)) {
                return false;
            }
            
            // Initialize notification settings map if null
            if (conversation.getNotificationSettings() == null) {
                conversation.setNotificationSettings(new HashMap<>());
            }
            
            // Toggle notification setting for this user
            boolean currentSetting = conversation.getNotificationSettings().getOrDefault(accountIdStr, true);
            conversation.getNotificationSettings().put(accountIdStr, !currentSetting);
            conversation.setUpdatedAt(LocalDateTime.now());
            
            conversationRepository.save(conversation);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Clears conversation state.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    @Override
    public boolean clearManualUnreadMark(String conversationId, String accountId) {
        try {
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) {
                return false;
            }
            
            // Check if user is a member of the conversation
            if (!conversation.getMembers().contains(accountId)) {
                return false;
            }
            
            // Remove user from manually marked as unread set
            if (conversation.getManuallyMarkedAsUnread() != null) {
                conversation.getManuallyMarkedAsUnread().remove(accountId);
                conversation.setUpdatedAt(LocalDateTime.now());
                conversationRepository.save(conversation);
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
