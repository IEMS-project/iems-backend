package com.iems.chatservice.service;

import com.iems.chatservice.entity.Message;

public interface IMessageDeletionService {
    boolean deleteForMe(String messageId, String userId);

    Message recallMessage(String messageId, String userId);
}
