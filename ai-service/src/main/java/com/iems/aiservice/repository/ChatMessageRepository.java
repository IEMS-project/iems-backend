package com.iems.aiservice.repository;

import com.iems.aiservice.entity.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByConversationIdOrderByTimestampAsc(String conversationId);

    void deleteByConversationId(String conversationId);
}