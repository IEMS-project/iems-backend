package com.iems.chatservice.service.Impl;

import com.iems.chatservice.dto.MemberResponseDto;
import com.iems.chatservice.dto.UserDetailDto;
import com.iems.chatservice.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.ConversationRepository;
import com.iems.chatservice.repository.MessageRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.Sort;

import java.util.*;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MessageService implements IMessageService {

    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private  MongoTemplate mongoTemplate;
    @Autowired
    private IMessageBroadcastService messageBroadcastService;
    @Autowired
    private IMessageReadService messageReadService;
    @Autowired
    private IMessageDeletionService messageDeletionService;
    @Autowired
    private IMessagePinService messagePinService;
    @Autowired
    private IMessageReactionService messageReactionService;
    
    
    @Autowired
    private IUserService userService;


    @Override
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
                Optional<UserDetailDto> userOpt = userService.getUserById(uuid);
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

    @Override
    public Message saveAndBroadcast(Message message) {
        return messageBroadcastService.saveAndBroadcast(message);
    }

    @Override
    public Page<Message> getRecentMessages(String conversationId, int page, int size) {
        return messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, PageRequest.of(page, size));
    }

    @Override
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

    @Override
    public void markAsRead(String conversationId) {
        UUID userId = userService.getUserIdFromRequest();
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

    @Override
    public Map<String, Integer> getUnreadCountsByUser() {
        UUID userId = userService.getUserIdFromRequest();
        return messageReadService.getUnreadCountsByUser(userId.toString());
    }

    @Override
    public int getUnreadCountForConversation(String conversationId) {
        UUID userId = userService.getUserIdFromRequest();
        return messageReadService.getUnreadCountForConversation(conversationId, userId.toString());
    }

    @Override
    public Message replyToMessage(String conversationId, String content, String replyToMessageId) {
        UUID userId = userService.getUserIdFromRequest();
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

    @Override
    public Message replyToMessage(String conversationId, String content, String replyToMessageId, String explicitSenderId) {
        String senderId = explicitSenderId;

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
    @Override
    public Message addReaction(String messageId, String emoji) {
        UUID userId = userService.getUserIdFromRequest();
        return messageReactionService.addReaction(messageId, userId.toString(), emoji);
    }

    // Remove reaction from message
    @Override
    public Message removeReaction(String messageId) {
        UUID userId = userService.getUserIdFromRequest();
        return messageReactionService.removeReaction(messageId, userId.toString());
    }

    // Delete message for user (delete for me)
    @Override
    public boolean deleteForMe(String messageId) {
        UUID userId = userService.getUserIdFromRequest();
        return messageDeletionService.deleteForMe(messageId, userId.toString());
    }

    // Recall message (delete for everyone)
    @Override
    public Message recallMessage(String messageId) {
        UUID userId = userService.getUserIdFromRequest();
        return messageDeletionService.recallMessage(messageId, userId.toString());
    }

    // Pin message
    @Override
    public Message pinMessage(String conversationId, String messageId) {
        UUID userId = userService.getUserIdFromRequest();
        return messagePinService.pinMessage(conversationId, messageId, userId.toString());
    }

    // Unpin message
    @Override
    public Message unpinMessage(String conversationId, String messageId) {
        UUID userId = userService.getUserIdFromRequest();
        return messagePinService.unpinMessage(conversationId, messageId, userId.toString());
    }

    // Get pinned messages for conversation
    @Override
    public List<Message> getPinnedMessages(String conversationId) {
        Query query = new Query(Criteria.where("conversationId").is(conversationId).and("pinned").is(true));
        query.with(Sort.by(Sort.Direction.DESC, "pinnedAt"));
        return mongoTemplate.find(query, Message.class);
    }

    // Enhanced mark as read with last read message tracking
    @Override
    public void markAsReadWithLastMessage(String conversationId, String lastMessageId) {
        UUID userId = userService.getUserIdFromRequest();
        messageReadService.markAsReadWithLastMessage(conversationId, userId.toString(), lastMessageId);
    }

    // Mark entire conversation as read
    @Override
    public boolean markConversationAsRead(String conversationId) {
        UUID userId = userService.getUserIdFromRequest();
        return messageReadService.markConversationAsRead(conversationId, userId.toString());
    }


    // Get messages with user-specific filtering (exclude deleted/recalled)
    @Override
    public List<Message> getMessagesForUser(String conversationId, int page, int size) {
        UUID userId = userService.getUserIdFromRequest();
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
    @Override
    public Map<String, Object> getConversationMessagesPaginated(String conversationId, int limit, String before) {
        UUID userId = userService.getUserIdFromRequest();
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

    // Get newest messages by type (IMAGE/VIDEO/FILE or MEDIA=both) with before cursor
    @Override
    public Map<String, Object> getLatestByType(String conversationId, String type, int limit, String before) {
        UUID userId = userService.getUserIdFromRequest();
        String userIdStr = userId.toString();

        String normalizedType = (type == null ? "MEDIA" : type).toUpperCase();
        boolean mediaBoth = normalizedType.equals("MEDIA") || normalizedType.equals("ALL");

        Criteria baseCriteria = new Criteria().andOperator(
            Criteria.where("conversationId").is(conversationId),
            new Criteria().orOperator(
                Criteria.where("deletedForUsers").exists(false),
                Criteria.where("deletedForUsers").nin(userIdStr)
            )
        );

        if (mediaBoth) {
            baseCriteria = new Criteria().andOperator(
                baseCriteria,
                Criteria.where("type").in("IMAGE", "VIDEO")
            );
        } else {
            baseCriteria = new Criteria().andOperator(
                baseCriteria,
                Criteria.where("type").is(normalizedType)
            );
        }

        Query query = new Query(baseCriteria);

        // Cursor: support ISO timestamp or messageId
        if (before != null && !before.isBlank()) {
            try {
                LocalDateTime beforeTime = LocalDateTime.parse(before);
                query.addCriteria(Criteria.where("sentAt").lt(beforeTime));
            } catch (Exception e) {
                try {
                    Message beforeMessage = messageRepository.findById(before).orElse(null);
                    if (beforeMessage != null) {
                        query.addCriteria(Criteria.where("sentAt").lt(beforeMessage.getSentAt()));
                    }
                } catch (Exception ignore) { }
            }
        }

        int lim = Math.max(1, Math.min(limit, 100));
        query.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        query.limit(lim + 1);

        List<Message> list = mongoTemplate.find(query, Message.class);
        boolean hasMore = list.size() > lim;
        if (hasMore) list.remove(list.size() - 1);

        String nextCursor = null;
        if (!list.isEmpty()) {
            Message oldest = list.get(list.size() - 1);
            nextCursor = oldest.getSentAt() != null ? oldest.getSentAt().toString() : null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("messages", list); // newest-first order
        result.put("hasMore", hasMore);
        result.put("nextCursor", nextCursor);
        result.put("type", normalizedType);
        return result;
    }

    // Broadcast helpers moved to MessageBroadcastService

    // Get message by ID with neighbors for jump-to-message functionality
    @Override
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
    @Override
    public List<Message> getMessagesBefore(Message targetMessage, int limit) {
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
    @Override
    public List<Message> getMessagesAfter(Message targetMessage, int limit) {
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

    // Get media messages around a specific message, filtered by type
    @Override
    public Map<String, Object> getMessagesAroundByType(String messageId, String type, int before, int after) {
        Message targetMessage = messageRepository.findById(messageId).orElse(null);
        if (targetMessage == null) {
            return null;
        }

        String normalizedType = (type == null ? "MEDIA" : type).toUpperCase();
        boolean mediaBoth = normalizedType.equals("MEDIA") || normalizedType.equals("ALL");

        // Before
        Criteria beforeCriteriaBase = new Criteria().andOperator(
            Criteria.where("conversationId").is(targetMessage.getConversationId()),
            Criteria.where("sentAt").lt(targetMessage.getSentAt()),
            Criteria.where("recalled").ne(true)
        );
        Query beforeQuery;
        if (mediaBoth) {
            beforeQuery = new Query(new Criteria().andOperator(
                beforeCriteriaBase,
                Criteria.where("type").in("IMAGE", "VIDEO")
            ));
        } else {
            beforeQuery = new Query(new Criteria().andOperator(beforeCriteriaBase, Criteria.where("type").is(normalizedType)));
        }
        beforeQuery.with(Sort.by(Sort.Direction.DESC, "sentAt"));
        beforeQuery.limit(Math.max(0, before));
        List<Message> beforeMessagesDesc = mongoTemplate.find(beforeQuery, Message.class);
        Collections.reverse(beforeMessagesDesc); // ascending

        // After
        Criteria afterCriteriaBase = new Criteria().andOperator(
            Criteria.where("conversationId").is(targetMessage.getConversationId()),
            Criteria.where("sentAt").gt(targetMessage.getSentAt()),
            Criteria.where("recalled").ne(true)
        );
        Query afterQuery;
        if (mediaBoth) {
            afterQuery = new Query(new Criteria().andOperator(
                afterCriteriaBase,
                Criteria.where("type").in("IMAGE", "VIDEO")
            ));
        } else {
            afterQuery = new Query(new Criteria().andOperator(afterCriteriaBase, Criteria.where("type").is(normalizedType)));
        }
        afterQuery.with(Sort.by(Sort.Direction.ASC, "sentAt"));
        afterQuery.limit(Math.max(0, after));
        List<Message> afterMessages = mongoTemplate.find(afterQuery, Message.class);

        Map<String, Object> result = new HashMap<>();
        result.put("targetMessage", targetMessage);
        result.put("beforeMessages", beforeMessagesDesc);
        result.put("afterMessages", afterMessages);
        result.put("conversationId", targetMessage.getConversationId());
        result.put("type", normalizedType);
        return result;
    }

    // Search messages by text content
    @Override
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
    @Override
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
    @Override
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



