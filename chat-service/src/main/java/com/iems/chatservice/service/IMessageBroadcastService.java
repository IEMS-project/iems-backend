package com.iems.chatservice.service;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;

import java.util.Map;

public interface IMessageBroadcastService {
    Message saveAndBroadcast(Message message);

    void broadcastMessageUpdate(Message message, String eventType, Map<String, Object> additionalData);

    void broadcastConversationUpdate(Conversation conversation, Message lastMessage);

    void broadcastConversationMetaUpdate(Conversation conversation, Map<String, Object> changedFields);

    Message getLatestVisibleMessageForUser(String conversationId, String accountId);
}
