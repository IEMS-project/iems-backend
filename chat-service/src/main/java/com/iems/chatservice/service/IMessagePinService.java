package com.iems.chatservice.service;

import com.iems.chatservice.entity.Message;

public interface IMessagePinService {
    /**
     * Pins message pin data for quick access.
     *
     * @param conversationId the conversation id parameter
     * @param messageId the message id parameter
     * @param accountId the account id parameter
     * @return the pin message result
     */
    Message pinMessage(String conversationId, String messageId, String accountId);

    /**
     * Unpins message pin data.
     *
     * @param conversationId the conversation id parameter
     * @param messageId the message id parameter
     * @param accountId the account id parameter
     * @return the unpin message result
     */
    Message unpinMessage(String conversationId, String messageId, String accountId);

}
