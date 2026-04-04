package com.iems.aiservice.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "chat_messages")
public class ChatMessage {
    @Id
    private String id;
    private String conversationId;
    private String role; // "user" or "assistant"
    private String content;
    private Instant timestamp;
}