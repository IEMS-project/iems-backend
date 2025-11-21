package com.iems.chatservice.service;

import java.util.Map;

public interface IMessageReadService {
    void markAsRead(String conversationId, String userId);

    Map<String, Integer> getUnreadCountsByUser(String userId);

    int getUnreadCountForConversation(String conversationId, String userId);

    void markAsReadWithLastMessage(String conversationId, String userId, String lastMessageId);

    boolean markConversationAsRead(String conversationId, String userId);

    void broadcastReadStatusUpdate(String conversationId, String userId, String lastMessageId);
}
