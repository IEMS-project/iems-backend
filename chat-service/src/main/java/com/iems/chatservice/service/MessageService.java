package com.iems.chatservice.service;

import com.iems.chatservice.client.UserServiceFeignClient;
import com.iems.chatservice.dto.MemberResponseDto;
import com.iems.chatservice.dto.UserDetailDto;
import com.iems.chatservice.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final MessageBroadcastService messageBroadcastService;
    private final MessageReadService messageReadService;
    private final MessageDeletionService messageDeletionService;
    private final MessagePinService messagePinService;
    private final MessageReactionService messageReactionService;

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
        return messageBroadcastService.saveAndBroadcast(message);
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

    public void markAsRead(String conversationId) {
        UUID userId = getUserIdFromRequest();
        String userIdStr = userId.toString();
        if (conversationId == null || conversationId.isBlank()) return;
        
        Criteria markCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            Criteria.where("senderId").ne(userIdStr),
            new Criteria().orOperator(
                Criteria.where("readBy").exists(false),
                Criteria.where("readBy").nin(userIdStr)
            )
        );
        Query q = new Query(markCriteria);
        Update up = new Update().addToSet("readBy", userIdStr);
        mongoTemplate.updateMulti(q, up, Message.class);
    }

    public Map<String, Integer> getUnreadCountsByUser() {
        UUID userId = getUserIdFromRequest();
        return messageReadService.getUnreadCountsByUser(userId.toString());
    }

    // Get unread count for specific conversation
    public int getUnreadCountForConversation(String conversationId) {
        UUID userId = getUserIdFromRequest();
        return messageReadService.getUnreadCountForConversation(conversationId, userId.toString());
    }

    // Reply message functionality
    public Message replyToMessage(String conversationId, String content, String replyToMessageId) {
        UUID userId = getUserIdFromRequest();
        String senderId = userId.toString();
        
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
    public Message addReaction(String messageId, String emoji) {
        UUID userId = getUserIdFromRequest();
        return messageReactionService.addReaction(messageId, userId.toString(), emoji);
    }

    // Remove reaction from message
    public Message removeReaction(String messageId) {
        UUID userId = getUserIdFromRequest();
        return messageReactionService.removeReaction(messageId, userId.toString());
    }

    // Delete message for user (delete for me)
    public boolean deleteForMe(String messageId) {
        UUID userId = getUserIdFromRequest();
        return messageDeletionService.deleteForMe(messageId, userId.toString());
    }

    // Recall message (delete for everyone)
    public Message recallMessage(String messageId) {
        UUID userId = getUserIdFromRequest();
        return messageDeletionService.recallMessage(messageId, userId.toString());
    }

    // Pin message
    public Message pinMessage(String conversationId, String messageId) {
        UUID userId = getUserIdFromRequest();
        return messagePinService.pinMessage(conversationId, messageId, userId.toString());
    }

    // Unpin message
    public Message unpinMessage(String conversationId, String messageId) {
        UUID userId = getUserIdFromRequest();
        return messagePinService.unpinMessage(conversationId, messageId, userId.toString());
    }

    // Get pinned messages for conversation
    public List<Message> getPinnedMessages(String conversationId) {
        Query query = new Query(Criteria.where("conversationId").is(conversationId).and("pinned").is(true));
        query.with(Sort.by(Sort.Direction.DESC, "pinnedAt"));
        return mongoTemplate.find(query, Message.class);
    }

    // Enhanced mark as read with last read message tracking
    public void markAsReadWithLastMessage(String conversationId, String lastMessageId) {
        UUID userId = getUserIdFromRequest();
        messageReadService.markAsReadWithLastMessage(conversationId, userId.toString(), lastMessageId);
    }

    // Mark entire conversation as read
    public boolean markConversationAsRead(String conversationId) {
        UUID userId = getUserIdFromRequest();
        return messageReadService.markConversationAsRead(conversationId, userId.toString());
    }


    // Get messages with user-specific filtering (exclude deleted/recalled)
    public List<Message> getMessagesForUser(String conversationId, int page, int size) {
        UUID userId = getUserIdFromRequest();
        String userIdStr = userId.toString();
        
        Criteria userMessagesCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            new Criteria().orOperator(
                Criteria.where("deletedForUsers").exists(false),
                Criteria.where("deletedForUsers").nin(userIdStr)
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
    public Map<String, Object> getConversationMessagesPaginated(String conversationId, int limit, String before) {
        UUID userId = getUserIdFromRequest();
        String userIdStr = userId.toString();
        
        Criteria baseCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            new Criteria().orOperator(
                Criteria.where("deletedForUsers").exists(false),
                Criteria.where("deletedForUsers").nin(userIdStr)
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

    // Broadcast helpers moved to MessageBroadcastService

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


    public UUID getUserIdFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();
        return userId;
    }

    public static class UnreadCountRow {
        public String id; // _id from group -> conversationId
        public Integer count;
    }
}



