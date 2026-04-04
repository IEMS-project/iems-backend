package com.iems.aiservice.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "conversations")
public class Conversation {
    @Id
    private String id;
    private String userId;
    private String name;
    private String projectId;
    private Instant createdAt;
    private Instant updatedAt;
}