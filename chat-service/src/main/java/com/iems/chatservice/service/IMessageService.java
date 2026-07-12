package com.iems.chatservice.service;

import com.iems.chatservice.dto.MemberResponseDto;
import com.iems.chatservice.entity.Message;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface IMessageService {

    /**
     * Retrieves message information.
     *
     * @param conversationId the conversation id parameter
     * @return the matching result collection
     */
    List<MemberResponseDto> getMembersByConversationId(String conversationId);

    /**
     * Saves message data.
     *
     * @param message the message parameter
     * @return the save and broadcast result
     */
    Message saveAndBroadcast(Message message);

    /**
     * Retrieves message information.
     *
     * @param conversationId the conversation id parameter
     * @param page the page parameter
     * @param size the size parameter
     * @return the paginated result set
     */
    Page<Message> getRecentMessages(String conversationId, int page, int size);

    /**
     * Retrieves message information.
     *
     * @param conversationId the conversation id parameter
     * @param before the before parameter
     * @param limit the limit parameter
     * @return the matching result collection
     */
    List<Message> getMessagesScroll(String conversationId, LocalDateTime before, int limit);

    /**
     * Marks message data according to the request.
     *
     * @param conversationId the conversation id parameter
     */
    void markAsRead(String conversationId);

    /**
     * Retrieves message information.
     *
     * @return the get unread counts by user result
     */
    Map<String, Integer> getUnreadCountsByUser();

    /**
     * Retrieves message information.
     *
     * @param conversationId the conversation id parameter
     * @return the get unread count for conversation result
     */
    int getUnreadCountForConversation(String conversationId);

    /**
     * Returns reply to message for message processing.
     *
     * @param conversationId the conversation id parameter
     * @param content the content parameter
     * @param replyToMessageId the reply to message id parameter
     * @return the reply to message result
     */
    Message replyToMessage(String conversationId, String content, String replyToMessageId);

    /**
     * Returns reply to message for message processing.
     *
     * @param conversationId the conversation id parameter
     * @param content the content parameter
     * @param replyToMessageId the reply to message id parameter
     * @param explicitSenderId the explicit sender id parameter
     * @return the reply to message result
     */
    Message replyToMessage(String conversationId, String content, String replyToMessageId, String explicitSenderId);

    // Add reaction to message
    /**
     * Adds message data for the request.
     *
     * @param messageId the message id parameter
     * @param emoji the emoji parameter
     * @return the add reaction result
     */
    Message addReaction(String messageId, String emoji);

    // Remove reaction from message
    /**
     * Removes message data for the request.
     *
     * @param messageId the message id parameter
     * @return the remove reaction result
     */
    Message removeReaction(String messageId);

    // Delete message for user (delete for me)
    /**
     * Deletes message data for the request.
     *
     * @param messageId the message id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    boolean deleteForMe(String messageId);

    // Recall message (delete for everyone)
    /**
     * Returns recall message for message processing.
     *
     * @param messageId the message id parameter
     * @return the recall message result
     */
    Message recallMessage(String messageId);

    // Pin message
    /**
     * Pins message data for quick access.
     *
     * @param conversationId the conversation id parameter
     * @param messageId the message id parameter
     * @return the pin message result
     */
    Message pinMessage(String conversationId, String messageId);

    // Unpin message
    /**
     * Unpins message data.
     *
     * @param conversationId the conversation id parameter
     * @param messageId the message id parameter
     * @return the unpin message result
     */
    Message unpinMessage(String conversationId, String messageId);

    // Get pinned messages for conversation
    /**
     * Retrieves message information.
     *
     * @param conversationId the conversation id parameter
     * @return the matching result collection
     */
    List<Message> getPinnedMessages(String conversationId);

    // Enhanced mark as read with last read message tracking
    /**
     * Marks message data according to the request.
     *
     * @param conversationId the conversation id parameter
     * @param lastMessageId the last message id parameter
     */
    void markAsReadWithLastMessage(String conversationId, String lastMessageId);

    // Mark entire conversation as read
    /**
     * Marks message data according to the request.
     *
     * @param conversationId the conversation id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    boolean markConversationAsRead(String conversationId);

    // Get messages with user-specific filtering (exclude deleted/recalled)
    /**
     * Retrieves message information.
     *
     * @param conversationId the conversation id parameter
     * @param page the page parameter
     * @param size the size parameter
     * @return the matching result collection
     */
    List<Message> getMessagesForUser(String conversationId, int page, int size);

    // Get paginated messages for conversation with cursor-based pagination
    /**
     * Retrieves message information.
     *
     * @param conversationId the conversation id parameter
     * @param limit the limit parameter
     * @param before the before parameter
     * @return the get conversation messages paginated result
     */
    Map<String, Object> getConversationMessagesPaginated(String conversationId, int limit, String before);

    // Get newest messages by type (IMAGE/VIDEO/FILE or MEDIA=both) with before cursor
    /**
     * Retrieves message information.
     *
     * @param conversationId the conversation id parameter
     * @param type the type parameter
     * @param limit the limit parameter
     * @param before the before parameter
     * @return the get latest by type result
     */
    Map<String, Object> getLatestByType(String conversationId, String type, int limit, String before);

    // Get message by ID with neighbors for jump-to-message functionality
    /**
     * Retrieves message information.
     *
     * @param messageId the message id parameter
     * @param withNeighbors the with neighbors parameter
     * @param neighborLimit the neighbor limit parameter
     * @return the get message with neighbors result
     */
    Map<String, Object> getMessageWithNeighbors(String messageId, boolean withNeighbors, int neighborLimit);

    // Get messages before a specific message
    /**
     * Retrieves message information.
     *
     * @param targetMessage the target message parameter
     * @param limit the limit parameter
     * @return the matching result collection
     */
    List<Message> getMessagesBefore(Message targetMessage, int limit);

    // Get messages after a specific message
    /**
     * Retrieves message information.
     *
     * @param targetMessage the target message parameter
     * @param limit the limit parameter
     * @return the matching result collection
     */
    List<Message> getMessagesAfter(Message targetMessage, int limit);

    // Get media messages around a specific message, filtered by type
    /**
     * Retrieves message information.
     *
     * @param messageId the message id parameter
     * @param type the type parameter
     * @param before the before parameter
     * @param after the after parameter
     * @return the get messages around by type result
     */
    Map<String, Object> getMessagesAroundByType(String messageId, String type, int before, int after);

    // Search messages by text content
    /**
     * Searches message information.
     *
     * @param conversationId the conversation id parameter
     * @param keyword the keyword parameter
     * @param page the page parameter
     * @param size the size parameter
     * @return the search messages result
     */
    Map<String, Object> searchMessages(String conversationId, String keyword, int page, int size);

    // Get messages around a specific message (for jump-to-message)
    /**
     * Retrieves message information.
     *
     * @param messageId the message id parameter
     * @param before the before parameter
     * @param after the after parameter
     * @return the get messages around result
     */
    Map<String, Object> getMessagesAround(String messageId, int before, int after);

    // Get messages between two message IDs (for gap filling)
    /**
     * Retrieves message information.
     *
     * @param fromMessageId the from message id parameter
     * @param toMessageId the to message id parameter
     * @param conversationId the conversation id parameter
     * @return the matching result collection
     */
    List<Message> getMessagesBetween(String fromMessageId, String toMessageId, String conversationId);
}
