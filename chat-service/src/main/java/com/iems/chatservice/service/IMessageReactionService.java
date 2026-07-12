package com.iems.chatservice.service;

import com.iems.chatservice.entity.Message;

public interface IMessageReactionService {
    /**
     * Adds message reaction data for the request.
     *
     * @param messageId the message id parameter
     * @param accountId the account id parameter
     * @param emoji the emoji parameter
     * @return the add reaction result
     */
    Message addReaction(String messageId, String accountId, String emoji);

    /**
     * Removes message reaction data for the request.
     *
     * @param messageId the message id parameter
     * @param accountId the account id parameter
     * @return the remove reaction result
     */
    Message removeReaction(String messageId, String accountId);
}
