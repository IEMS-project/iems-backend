package com.iems.chatservice.service;

import com.iems.chatservice.entity.Message;

public interface IMessageDeletionService {
    /**
     * Deletes message deletion data for the request.
     *
     * @param messageId the message id parameter
     * @param accountId the account id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    boolean deleteForMe(String messageId, String accountId);

    /**
     * Returns recall message for message deletion processing.
     *
     * @param messageId the message id parameter
     * @param accountId the account id parameter
     * @return the recall message result
     */
    Message recallMessage(String messageId, String accountId);
}
