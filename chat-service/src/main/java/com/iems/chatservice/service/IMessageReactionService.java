package com.iems.chatservice.service;

import com.iems.chatservice.entity.Message;

public interface IMessageReactionService {
    Message addReaction(String messageId, String userId, String emoji);

    Message removeReaction(String messageId, String userId);
}
