package com.iems.chatservice.service;

import com.iems.chatservice.entity.Message;

public interface IMessageDeletionService {
    boolean deleteForMe(String messageId, String accountId);

    Message recallMessage(String messageId, String accountId);
}
