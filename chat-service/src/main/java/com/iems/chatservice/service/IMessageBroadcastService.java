package com.iems.chatservice.service;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;

import java.util.Map;

public interface IMessageBroadcastService {
    /**
     * Saves message broadcast data.
     *
     * @param message the message parameter
     * @return the save and broadcast result
     */
    Message saveAndBroadcast(Message message);

    /**
     * Performs broadcast message update for message broadcast processing.
     *
     * @param message the message parameter
     * @param eventType the event type parameter
     * @param additionalData the additional data parameter
     */
    void broadcastMessageUpdate(Message message, String eventType, Map<String, Object> additionalData);

    /**
     * Performs broadcast conversation update for message broadcast processing.
     *
     * @param conversation the conversation parameter
     * @param lastMessage the last message parameter
     */
    void broadcastConversationUpdate(Conversation conversation, Message lastMessage);

    /**
     * Performs broadcast conversation meta update for message broadcast processing.
     *
     * @param conversation the conversation parameter
     * @param changedFields the changed fields parameter
     */
    void broadcastConversationMetaUpdate(Conversation conversation, Map<String, Object> changedFields);

    /**
     * Retrieves message broadcast information.
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     * @return the get latest visible message for user result
     */
    Message getLatestVisibleMessageForUser(String conversationId, String accountId);
}
