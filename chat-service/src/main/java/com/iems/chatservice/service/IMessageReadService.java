package com.iems.chatservice.service;

import java.util.Map;

public interface IMessageReadService {
    void markAsRead(String conversationId, String accountId);

    Map<String, Integer> getUnreadCountsByUser(String accountId);

    int getUnreadCountForConversation(String conversationId, String accountId);

    void markAsReadWithLastMessage(String conversationId, String accountId, String lastMessageId);

    boolean markConversationAsRead(String conversationId, String accountId);

    void broadcastReadStatusUpdate(String conversationId, String accountId, String lastMessageId);
}
