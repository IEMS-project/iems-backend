package com.iems.chatservice.service;

import com.iems.chatservice.entity.Message;

public interface IMessagePinService {
    Message pinMessage(String conversationId, String messageId, String userId);

    Message unpinMessage(String conversationId, String messageId, String userId);

}
