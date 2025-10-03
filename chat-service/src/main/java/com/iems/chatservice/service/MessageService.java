package com.iems.chatservice.service;

import com.iems.chatservice.client.UserServiceFeignClient;
import com.iems.chatservice.dto.MemberResponseDto;
import com.iems.chatservice.dto.UserDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.ConversationRepository;
import com.iems.chatservice.repository.MessageRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.Sort;

import java.util.*;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MongoTemplate mongoTemplate;

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    private Optional<UserDetailDto> getUserById(UUID userId) {
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUserById(userId);

            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userData = (Map<String, Object>) response.getBody().get("data");
                return Optional.of(convertToUserDetailDto(userData));
            }

            return Optional.empty();
        } catch (Exception e) {
            // Log error and return empty
            System.err.println("Error fetching user " + userId + " from User Service: " + e.getMessage());
            return Optional.empty();
        }
    }
    private UserDetailDto convertToUserDetailDto(Map<String, Object> userData) {
        UserDetailDto dto = new UserDetailDto();
        dto.setId(UUID.fromString(userData.get("id").toString()));
        dto.setFirstName((String) userData.get("firstName"));
        dto.setLastName((String) userData.get("lastName"));
        dto.setEmail((String) userData.get("email"));
        dto.setAddress((String) userData.get("address"));
        dto.setPhone((String) userData.get("phone"));

        // Handle Date objects - convert to string
        Object dob = userData.get("dob");
        dto.setDob(dob != null ? dob.toString() : null);

        // Handle enum objects - convert to string
        Object gender = userData.get("gender");
        dto.setGender(gender != null ? gender.toString() : null);

        dto.setPersonalID((String) userData.get("personalID"));
        dto.setImage((String) userData.get("image"));
        dto.setBankAccountNumber((String) userData.get("bankAccountNumber"));
        dto.setBankName((String) userData.get("bankName"));

        // Handle enum objects - convert to string
        Object contractType = userData.get("contractType");
        dto.setContractType(contractType != null ? contractType.toString() : null);

        // Handle Date objects - convert to string
        Object startDate = userData.get("startDate");
        dto.setStartDate(startDate != null ? startDate.toString() : null);

        dto.setRole((String) userData.get("role"));
        return dto;
    }

    public List<MemberResponseDto> getMembersByConversationId(String conversationId) {
        Optional<Conversation> conversationOpt = conversationRepository.findById(conversationId);
        if (conversationOpt.isEmpty()) {
            return Collections.emptyList();
        }

        Conversation conversation = conversationOpt.get();
        List<String> memberIds = conversation.getMembers();
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<MemberResponseDto> members = new ArrayList<>();

        for (String memberId : memberIds) {
            try {
                UUID uuid = UUID.fromString(memberId);
                Optional<UserDetailDto> userOpt = getUserById(uuid);
                userOpt.ifPresent(user -> {
                    members.add(MemberResponseDto.builder()
                            .userId(user.getId())
                            .userName(user.getFirstName() + " " + user.getLastName())
                            .userEmail(user.getEmail())
                            .userImage(user.getImage())
                            .build());
                });
            } catch (Exception e) {
                System.err.println("Invalid memberId in conversation " + conversationId + ": " + memberId);
            }
        }

        return members;
    }

    public Message saveAndBroadcast(Message message) {
        // Persist
        Message saved = messageRepository.save(message);

        // Fetch conversation to decide routing
        Conversation conv = conversationRepository.findById(saved.getConversationId()).orElse(null);
        if (conv != null) {
            // For demo simplicity: always broadcast to the conversation topic so browser clients receive without user session mapping
            messagingTemplate.convertAndSend("/topic/conversations/" + conv.getId(), saved);
            // Optionally still fan-out to user queues if client uses user destinations later
            if (!"GROUP".equalsIgnoreCase(conv.getType())) {
                List<String> members = conv.getMembers();
                for (String memberId : members) {
                    messagingTemplate.convertAndSendToUser(memberId, "/queue/messages", saved);
                }
            }

            // Also send lightweight user-updates to each member so sidebars can update last message in realtime
            try {
                List<String> members = conv.getMembers();
                if (members != null) {
                    for (String memberId : members) {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("event", "message");
                        payload.put("conversationId", conv.getId());
                        payload.put("senderId", saved.getSenderId());
                        payload.put("content", saved.getContent());
                        payload.put("type", saved.getType());
                        payload.put("messageId", saved.getId());
                        payload.put("timestamp", saved.getSentAt().toString());
                        
                        // Add unread count for members who haven't read this message (excluding sender)
                        if (!memberId.equals(saved.getSenderId())) {
                            int unreadCount = getUnreadCountForConversation(conv.getId(), memberId);
                            payload.put("unreadCount", unreadCount);
                        } else {
                            payload.put("unreadCount", 0);
                        }
                        
                        messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
                    }
                    
                    // Broadcast conversation list update
                    broadcastConversationUpdate(conv, saved);
                }
            } catch (Exception ignore) { }
        }
        return saved;
    }

    public Page<Message> getRecentMessages(String conversationId, int page, int size) {
        return messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, PageRequest.of(page, size));
    }

    public List<Message> getMessagesScroll(String conversationId, LocalDateTime before, int limit) {
        Query q = new Query();
        q.addCriteria(Criteria.where("conversationId").is(conversationId));
        if (before != null) {
            q.addCriteria(Criteria.where("sentAt").lt(before));
        }
        q.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        q.limit(Math.max(1, Math.min(limit, 100)));
        return mongoTemplate.find(q, Message.class);
    }

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
                        Criteria.where("recalled").ne(true), // Exclude recalled messages
                        new Criteria().orOperator(
                                Criteria.where("deletedForUsers").exists(false),
                                Criteria.where("deletedForUsers").nin(userId)
                        ), // Exclude messages deleted by user
                        new Criteria().orOperator(
                                Criteria.where("readBy").exists(false),
                                Criteria.where("readBy").nin(userId)
                        )
                )),
                Aggregation.group("conversationId").count().as("count")
        );
        AggregationResults<UnreadCountRow> res = mongoTemplate.aggregate(agg, "messages", UnreadCountRow.class);
        Map<String, Integer> map = new HashMap<>();
        for (UnreadCountRow row : res.getMappedResults()) {
            if (row != null && row.id != null) {
                map.put(row.id, row.count);
            }
        }
        return map;
    }

    // Get unread count for specific conversation
    public int getUnreadCountForConversation(String conversationId, String userId) {
        if (userId == null || userId.isBlank() || conversationId == null || conversationId.isBlank()) {
            return 0;
        }
        
        // Combine all criteria into a single $and query to avoid MongoDB BasicDocument limitations
        Criteria combinedCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            Criteria.where("senderId").ne(userId), // Exclude own messages
            Criteria.where("recalled").ne(true), // Exclude recalled messages
            new Criteria().orOperator(
                Criteria.where("deletedForUsers").exists(false),
                Criteria.where("deletedForUsers").nin(userId)
            ), // Exclude messages deleted by user
            new Criteria().orOperator(
                Criteria.where("readBy").exists(false),
                Criteria.where("readBy").nin(userId)
            ) // Only unread messages
        );
        
        Query query = new Query(combinedCriteria);
        return (int) mongoTemplate.count(query, Message.class);
    }

    // Reply message functionality
    public Message replyToMessage(String conversationId, String senderId, String content, String replyToMessageId) {
        // Get original message for context
        Message originalMessage = messageRepository.findById(replyToMessageId).orElse(null);
        if (originalMessage == null) {
            throw new RuntimeException("Original message not found");
        }

        Message reply = new Message();
        reply.setConversationId(conversationId);
        reply.setSenderId(senderId);
        reply.setContent(content);
        reply.setReplyToMessageId(replyToMessageId);
        reply.setReplyToContent(originalMessage.getContent());
        reply.setReplyToSenderId(originalMessage.getSenderId());

        return saveAndBroadcast(reply);
    }

    // Add reaction to message
    public Message addReaction(String messageId, String userId, String emoji) {
        Query query = new Query(Criteria.where("id").is(messageId));
        Update update = new Update().addToSet("reactions." + emoji, userId);
        
        mongoTemplate.updateFirst(query, update, Message.class);
        Message updatedMessage = messageRepository.findById(messageId).orElse(null);
        
        if (updatedMessage != null) {
            // Broadcast reaction update
            broadcastMessageUpdate(updatedMessage, "reaction_added", Map.of("userId", userId, "emoji", emoji));
        }
        
        return updatedMessage;
    }

    // Remove reaction from message
    public Message removeReaction(String messageId, String userId) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return null;

        Map<String, List<String>> reactions = message.getReactions();
        if (reactions != null) {
            String removedEmoji = null;
            for (Map.Entry<String, List<String>> entry : reactions.entrySet()) {
                if (entry.getValue().contains(userId)) {
                    entry.getValue().remove(userId);
                    if (entry.getValue().isEmpty()) {
                        reactions.remove(entry.getKey());
                    }
                    removedEmoji = entry.getKey();
                    break;
                }
            }
            
            message.setReactions(reactions);
            Message saved = messageRepository.save(message);
            
            if (removedEmoji != null) {
                broadcastMessageUpdate(saved, "reaction_removed", Map.of("userId", userId, "emoji", removedEmoji));
            }
            
            return saved;
        }
        
        return message;
    }

    // Delete message for user (delete for me)
    public boolean deleteForMe(String messageId, String userId) {
        // First check if message exists and user has access
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) {
            return false;
        }

        // Check if user is member of the conversation
        Conversation conversation = conversationRepository.findById(message.getConversationId()).orElse(null);
        if (conversation == null || !conversation.getMembers().contains(userId)) {
            return false;
        }

        // Check if already deleted for this user
        if (message.getDeletedForUsers() != null && message.getDeletedForUsers().contains(userId)) {
            return true; // Already deleted, consider success
        }

        // Add user to deletedForUsers list
        Query query = new Query(Criteria.where("id").is(messageId));
        Update update = new Update().addToSet("deletedForUsers", userId);
        
        mongoTemplate.updateFirst(query, update, Message.class);
        
        // Broadcast delete update to other clients
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
            // Send user-specific conversation update with the latest visible message
            Message latestVisible = getLatestVisibleMessageForUser(message.getConversationId(), userId);
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
            convUpdate.put("unreadCount", getUnreadCountForConversation(message.getConversationId(), userId));
            messagingTemplate.convertAndSend("/topic/user-updates/" + userId, convUpdate);
        } catch (Exception e) {
            // Log but don't fail the operation
            System.err.println("Failed to broadcast delete event: " + e.getMessage());
        }
        
        return true;
    }

    // Recall message (delete for everyone)
    public Message recallMessage(String messageId, String userId) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return null;

        // Only sender can recall message
        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("Only sender can recall message");
        }

        message.setRecalled(true);
        message.setRecalledAt(LocalDateTime.now());
        Message saved = messageRepository.save(message);

        // Broadcast recall update
        broadcastMessageUpdate(saved, "message_recalled", Map.of("recalledBy", userId));
        
        // Also broadcast conversation list update for sidebars
        Conversation conv = conversationRepository.findById(saved.getConversationId()).orElse(null);
        if (conv != null) {
            broadcastConversationUpdate(conv, saved);
        }
        
        return saved;
    }

    // Pin message
    public Message pinMessage(String conversationId, String messageId, String userId) {
        // Update message
        Query messageQuery = new Query(Criteria.where("id").is(messageId));
        Update messageUpdate = new Update()
                .set("pinned", true)
                .set("pinnedBy", userId)
                .set("pinnedAt", LocalDateTime.now());
        
        mongoTemplate.updateFirst(messageQuery, messageUpdate, Message.class);

        // Update conversation
        Query convQuery = new Query(Criteria.where("id").is(conversationId));
        Update convUpdate = new Update().addToSet("pinnedMessageIds", messageId);
        mongoTemplate.updateFirst(convQuery, convUpdate, Conversation.class);

        Message pinnedMessage = messageRepository.findById(messageId).orElse(null);
        if (pinnedMessage != null) {
            broadcastMessageUpdate(pinnedMessage, "message_pinned", Map.of("pinnedBy", userId));
        }

        return pinnedMessage;
    }

    // Unpin message
    public Message unpinMessage(String conversationId, String messageId, String userId) {
        // Update message
        Query messageQuery = new Query(Criteria.where("id").is(messageId));
        Update messageUpdate = new Update()
                .set("pinned", false)
                .unset("pinnedBy")
                .unset("pinnedAt");
        
        mongoTemplate.updateFirst(messageQuery, messageUpdate, Message.class);

        // Update conversation
        Query convQuery = new Query(Criteria.where("id").is(conversationId));
        Update convUpdate = new Update().pull("pinnedMessageIds", messageId);
        mongoTemplate.updateFirst(convQuery, convUpdate, Conversation.class);

        Message unpinnedMessage = messageRepository.findById(messageId).orElse(null);
        if (unpinnedMessage != null) {
            broadcastMessageUpdate(unpinnedMessage, "message_unpinned", Map.of("unpinnedBy", userId));
        }

        return unpinnedMessage;
    }

    // Get pinned messages for conversation
    public List<Message> getPinnedMessages(String conversationId) {
        Query query = new Query(Criteria.where("conversationId").is(conversationId).and("pinned").is(true));
        query.with(Sort.by(Sort.Direction.DESC, "pinnedAt"));
        return mongoTemplate.find(query, Message.class);
    }

    // Enhanced mark as read with last read message tracking
    public void markAsReadWithLastMessage(String conversationId, String userId, String lastMessageId) {
        // Mark messages as read
        markAsRead(conversationId, userId);
        
        // Update last read message in conversation
        if (lastMessageId != null) {
            Query convQuery = new Query(Criteria.where("id").is(conversationId));
            Update convUpdate = new Update().set("lastReadMessageId." + userId, lastMessageId);
            mongoTemplate.updateFirst(convQuery, convUpdate, Conversation.class);
        }

        // Broadcast read status update
        broadcastReadStatusUpdate(conversationId, userId, lastMessageId);
    }

    // Mark entire conversation as read
    public boolean markConversationAsRead(String conversationId, String userId) {
        // Verify user is member of conversation
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null || !conversation.getMembers().contains(userId)) {
            return false;
        }

        // Mark all unread messages in this conversation as read
        Criteria markReadCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            Criteria.where("senderId").ne(userId), // Don't mark own messages
            new Criteria().orOperator(
                Criteria.where("readBy").exists(false),
                Criteria.where("readBy").nin(userId)
            )
        );
        Query query = new Query(markReadCriteria);

        Update update = new Update().addToSet("readBy", userId);
        mongoTemplate.updateMulti(query, update, Message.class);

        // Get the latest message to update lastReadMessageId
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

        // Broadcast read status update
        broadcastReadStatusUpdate(conversationId, userId, latestMessage != null ? latestMessage.getId() : null);
        
        return true;
    }

    // Broadcast read status update to all conversation members
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
            
            // Send to all members
            for (String memberId : conv.getMembers()) {
                messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
            }
        }
    }

    // Get messages with user-specific filtering (exclude deleted/recalled)
    public List<Message> getMessagesForUser(String conversationId, String userId, int page, int size) {
        Criteria userMessagesCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            new Criteria().orOperator(
                Criteria.where("deletedForUsers").exists(false),
                Criteria.where("deletedForUsers").nin(userId)
            )
        );
        
        Query query = new Query(userMessagesCriteria);
        query.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        query.skip(page * size).limit(size);
        
        List<Message> messages = mongoTemplate.find(query, Message.class);
        Collections.reverse(messages); // Return in ascending order
        return messages;
    }

    // Get paginated messages for conversation with cursor-based pagination
    public Map<String, Object> getConversationMessagesPaginated(String conversationId, String userId, int limit, String before) {
        Criteria baseCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            new Criteria().orOperator(
                Criteria.where("deletedForUsers").exists(false),
                Criteria.where("deletedForUsers").nin(userId)
            )
        );
        
        Query query = new Query(baseCriteria);
        
        // Add cursor condition if provided
        if (before != null && !before.isBlank()) {
            try {
                // Try parsing as timestamp first
                LocalDateTime beforeTime = LocalDateTime.parse(before);
                query.addCriteria(Criteria.where("sentAt").lt(beforeTime));
            } catch (Exception e) {
                // If not timestamp, try as message ID
                try {
                    Message beforeMessage = messageRepository.findById(before).orElse(null);
                    if (beforeMessage != null) {
                        query.addCriteria(Criteria.where("sentAt").lt(beforeMessage.getSentAt()));
                    }
                } catch (Exception ex) {
                    // Invalid cursor, ignore
                }
            }
        }
        
        query.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        query.limit(limit + 1); // Fetch one extra to check if there are more
        
        List<Message> messages = mongoTemplate.find(query, Message.class);
        
        boolean hasMore = messages.size() > limit;
        if (hasMore) {
            messages.remove(messages.size() - 1); // Remove the extra message
        }
        
        Collections.reverse(messages); // Return in ascending order (oldest first)
        
        String nextCursor = null;
        if (hasMore && !messages.isEmpty()) {
            Message oldestMessage = messages.get(0);
            nextCursor = oldestMessage.getSentAt().toString();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("messages", messages);
        result.put("hasMore", hasMore);
        result.put("nextCursor", nextCursor);
        result.put("total", messages.size());
        
        return result;
    }

    // Broadcast message updates for realtime features
    private void broadcastMessageUpdate(Message message, String eventType, Map<String, Object> additionalData) {
        Conversation conv = conversationRepository.findById(message.getConversationId()).orElse(null);
        if (conv != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", eventType);
            payload.put("messageId", message.getId());
            payload.put("conversationId", message.getConversationId());
            payload.put("message", message);
            payload.putAll(additionalData);

            // Broadcast to conversation
            messagingTemplate.convertAndSend("/topic/conversations/" + conv.getId(), payload);
            
            // Also send to user updates
            for (String memberId : conv.getMembers()) {
                messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
            }
        }
    }
    
    private void broadcastConversationUpdate(Conversation conv, Message lastMessage) {
        try {
            List<String> members = conv.getMembers();
            if (members != null) {
                for (String memberId : members) {
                    Map<String, Object> conversationUpdate = new HashMap<>();
                    conversationUpdate.put("event", "conversation_updated");
                    conversationUpdate.put("conversationId", conv.getId());
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
                    conversationUpdate.put("unreadCount", getUnreadCountForConversation(conv.getId(), memberId));
                    
                    messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, conversationUpdate);
                }
            }
        } catch (Exception e) {
            System.err.println("Error broadcasting conversation update: " + e.getMessage());
        }
    }

    // Latest visible message for a specific user (exclude recalled and deleted-for-user)
    private Message getLatestVisibleMessageForUser(String conversationId, String userId) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("conversationId").is(conversationId),
                Criteria.where("recalled").ne(true),
                new Criteria().orOperator(
                        Criteria.where("deletedForUsers").exists(false),
                        Criteria.where("deletedForUsers").nin(userId)
                )
        );
        Query q = new Query(criteria);
        q.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        q.limit(1);
        return mongoTemplate.findOne(q, Message.class);
    }

    // Get message by ID with neighbors for jump-to-message functionality
    public Map<String, Object> getMessageWithNeighbors(String messageId, boolean withNeighbors, int neighborLimit) {
        // First, get the target message
        Message targetMessage = messageRepository.findById(messageId).orElse(null);
        if (targetMessage == null) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("targetMessage", targetMessage);
        result.put("conversationId", targetMessage.getConversationId());

        if (!withNeighbors) {
            return result;
        }

        // Get older messages (before the target)
        List<Message> olderMessages = getMessagesBefore(targetMessage, neighborLimit / 2);
        
        // Get newer messages (after the target)
        List<Message> newerMessages = getMessagesAfter(targetMessage, neighborLimit / 2);

        result.put("olderMessages", olderMessages);
        result.put("newerMessages", newerMessages);
        result.put("hasOlderMessages", !olderMessages.isEmpty());
        result.put("hasNewerMessages", !newerMessages.isEmpty());

        return result;
    }

    // Get messages before a specific message
    private List<Message> getMessagesBefore(Message targetMessage, int limit) {
        Criteria criteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(targetMessage.getConversationId()),
            Criteria.where("sentAt").lt(targetMessage.getSentAt()),
            Criteria.where("recalled").ne(true) // Exclude recalled messages
        );
        
        Query query = new Query(criteria);
        query.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        query.limit(limit);
        
        List<Message> messages = mongoTemplate.find(query, Message.class);
        Collections.reverse(messages); // Return in ascending order
        return messages;
    }

    // Get messages after a specific message
    private List<Message> getMessagesAfter(Message targetMessage, int limit) {
        Criteria criteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(targetMessage.getConversationId()),
            Criteria.where("sentAt").gt(targetMessage.getSentAt()),
            Criteria.where("recalled").ne(true) // Exclude recalled messages
        );
        
        Query query = new Query(criteria);
        query.with(Sort.by(Sort.Direction.ASC, "sentAt"));
        query.limit(limit);
        
        return mongoTemplate.find(query, Message.class);
    }

    // Search messages by text content
    public Map<String, Object> searchMessages(String conversationId, String keyword, int page, int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Map.of("messages", List.of(), "total", 0, "page", page, "size", size);
        }

        // Create text search criteria
        Criteria searchCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            Criteria.where("content").regex(keyword, "i"), // Case-insensitive regex search
            Criteria.where("recalled").ne(true) // Exclude recalled messages
        );

        Query query = new Query(searchCriteria);
        query.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        query.skip(page * size).limit(size);

        List<Message> messages = mongoTemplate.find(query, Message.class);
        
        // Get total count for pagination
        long totalCount = mongoTemplate.count(new Query(searchCriteria), Message.class);

        // Create search results with snippets
        List<Map<String, Object>> searchResults = new ArrayList<>();
        for (Message message : messages) {
            Map<String, Object> result = new HashMap<>();
            result.put("id", message.getId());
            result.put("senderId", message.getSenderId());
            result.put("content", message.getContent());
            result.put("sentAt", message.getSentAt());
            result.put("type", message.getType());
            
            // Create snippet (first 100 chars around keyword)
            String content = message.getContent();
            String snippet = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            result.put("snippet", snippet);
            
            searchResults.add(result);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("messages", searchResults);
        response.put("total", totalCount);
        response.put("page", page);
        response.put("size", size);
        response.put("hasMore", (page + 1) * size < totalCount);

        return response;
    }

    // Get messages around a specific message (for jump-to-message)
    public Map<String, Object> getMessagesAround(String messageId, int before, int after) {
        // First, get the target message
        Message targetMessage = messageRepository.findById(messageId).orElse(null);
        if (targetMessage == null) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("targetMessage", targetMessage);
        result.put("conversationId", targetMessage.getConversationId());

        // Get messages before the target
        List<Message> beforeMessages = getMessagesBefore(targetMessage, before);
        
        // Get messages after the target
        List<Message> afterMessages = getMessagesAfter(targetMessage, after);

        result.put("beforeMessages", beforeMessages);
        result.put("afterMessages", afterMessages);
        result.put("hasBeforeMessages", !beforeMessages.isEmpty());
        result.put("hasAfterMessages", !afterMessages.isEmpty());

        return result;
    }

    // Get messages between two message IDs (for gap filling)
    public List<Message> getMessagesBetween(String fromMessageId, String toMessageId, String conversationId) {
        // Get the two boundary messages
        Message fromMessage = messageRepository.findById(fromMessageId).orElse(null);
        Message toMessage = messageRepository.findById(toMessageId).orElse(null);
        
        if (fromMessage == null || toMessage == null) {
            return List.of();
        }

        // Determine which message is older
        Message olderMessage, newerMessage;
        if (fromMessage.getSentAt().isBefore(toMessage.getSentAt())) {
            olderMessage = fromMessage;
            newerMessage = toMessage;
        } else {
            olderMessage = toMessage;
            newerMessage = fromMessage;
        }

        // Get messages between the two timestamps
        Criteria betweenCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            Criteria.where("sentAt").gt(olderMessage.getSentAt()),
            Criteria.where("sentAt").lt(newerMessage.getSentAt()),
            Criteria.where("recalled").ne(true) // Exclude recalled messages
        );

        Query query = new Query(betweenCriteria);
        query.with(Sort.by(Sort.Direction.ASC, "sentAt"));

        return mongoTemplate.find(query, Message.class);
    }

    public static class UnreadCountRow {
        public String id; // _id from group -> conversationId
        public Integer count;
    }
}



