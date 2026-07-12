package com.iems.chatservice.service;

import java.util.Map;

public interface IMessageReadService {
    /**
     * Marks message read data according to the request.
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     */
    void markAsRead(String conversationId, String accountId);

    /**
     * Retrieves message read information.
     *
     * @param accountId the account id parameter
     * @return the get unread counts by user result
     */
    Map<String, Integer> getUnreadCountsByUser(String accountId);

    /**
     * Retrieves message read information.
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     * @return the get unread count for conversation result
     */
    int getUnreadCountForConversation(String conversationId, String accountId);

    /**
     * Marks message read data according to the request.
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     * @param lastMessageId the last message id parameter
     */
    void markAsReadWithLastMessage(String conversationId, String accountId, String lastMessageId);

    /**
     * Marks message read data according to the request.
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    boolean markConversationAsRead(String conversationId, String accountId);

    /**
     * Performs broadcast read status update for message read processing.
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     * @param lastMessageId the last message id parameter
     */
    void broadcastReadStatusUpdate(String conversationId, String accountId, String lastMessageId);
}
