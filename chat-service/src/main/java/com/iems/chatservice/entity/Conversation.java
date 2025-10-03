package com.iems.chatservice.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    private String id;

    private String type; // DIRECT, GROUP

    private String name;

    private List<String> members;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    // Track last read message for each user to optimize unread count
    private Map<String, String> lastReadMessageId; // userId -> messageId

    // Pinned messages in this conversation
    private List<String> pinnedMessageIds;

    // Conversation settings
    private String createdBy; // userId who created the conversation
    private String description; // Optional description for group chats
    private String avatarUrl; // Optional avatar for group chats
}
