package com.iems.chatservice.service;

import com.iems.chatservice.dto.MemberResponseDto;
import com.iems.chatservice.entity.Message;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface IMessageService {

    List<MemberResponseDto> getMembersByConversationId(String conversationId);

    Message saveAndBroadcast(Message message);

    Page<Message> getRecentMessages(String conversationId, int page, int size);

    List<Message> getMessagesScroll(String conversationId, LocalDateTime before, int limit);

    void markAsRead(String conversationId);

    Map<String, Integer> getUnreadCountsByUser();

    int getUnreadCountForConversation(String conversationId);

    Message replyToMessage(String conversationId, String content, String replyToMessageId);

    Message replyToMessage(String conversationId, String content, String replyToMessageId, String explicitSenderId);

    // Add reaction to message
    Message addReaction(String messageId, String emoji);

    // Remove reaction from message
    Message removeReaction(String messageId);

    // Delete message for user (delete for me)
    boolean deleteForMe(String messageId);

    // Recall message (delete for everyone)
    Message recallMessage(String messageId);

    // Pin message
    Message pinMessage(String conversationId, String messageId);

    // Unpin message
    Message unpinMessage(String conversationId, String messageId);

    // Get pinned messages for conversation
    List<Message> getPinnedMessages(String conversationId);

    // Enhanced mark as read with last read message tracking
    void markAsReadWithLastMessage(String conversationId, String lastMessageId);

    // Mark entire conversation as read
    boolean markConversationAsRead(String conversationId);

    // Get messages with user-specific filtering (exclude deleted/recalled)
    List<Message> getMessagesForUser(String conversationId, int page, int size);

    // Get paginated messages for conversation with cursor-based pagination
    Map<String, Object> getConversationMessagesPaginated(String conversationId, int limit, String before);

    // Get newest messages by type (IMAGE/VIDEO/FILE or MEDIA=both) with before cursor
    Map<String, Object> getLatestByType(String conversationId, String type, int limit, String before);

    // Get message by ID with neighbors for jump-to-message functionality
    Map<String, Object> getMessageWithNeighbors(String messageId, boolean withNeighbors, int neighborLimit);

    // Get messages before a specific message
    List<Message> getMessagesBefore(Message targetMessage, int limit);

    // Get messages after a specific message
    List<Message> getMessagesAfter(Message targetMessage, int limit);

    // Get media messages around a specific message, filtered by type
    Map<String, Object> getMessagesAroundByType(String messageId, String type, int before, int after);

    // Search messages by text content
    Map<String, Object> searchMessages(String conversationId, String keyword, int page, int size);

    // Get messages around a specific message (for jump-to-message)
    Map<String, Object> getMessagesAround(String messageId, int before, int after);

    // Get messages between two message IDs (for gap filling)
    List<Message> getMessagesBetween(String fromMessageId, String toMessageId, String conversationId);
}
