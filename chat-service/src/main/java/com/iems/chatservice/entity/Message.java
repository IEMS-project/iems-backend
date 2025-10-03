package com.iems.chatservice.entity;

import org.springframework.data.annotation.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Document(collection = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    private String id;

    private String conversationId;

    private String senderId;

    private String content;

    private String type = "TEXT"; // TEXT, IMAGE, FILE, STICKER

    private LocalDateTime sentAt = LocalDateTime.now();

    private Set<String> readBy; // userId đã đọc

    // Reply functionality
    private String replyToMessageId; // ID của tin nhắn được reply
    private String replyToContent; // Content của tin nhắn được reply (for display)
    private String replyToSenderId; // Sender của tin nhắn được reply

    // Reactions functionality
    private Map<String, List<String>> reactions; // emoji -> List<userId>

    // Delete functionality
    private Set<String> deletedForUsers; // userId đã xóa tin nhắn này (delete for me)
    private boolean recalled = false; // Thu hồi tin nhắn (delete for everyone)
    private LocalDateTime recalledAt; // Thời gian thu hồi

    // Pin functionality
    private boolean pinned = false;
    private String pinnedBy; // userId đã ghim
    private LocalDateTime pinnedAt; // Thời gian ghim

    // Edited functionality
    private boolean edited = false;
    private LocalDateTime editedAt;
    private List<String> editHistory; // Lịch sử chỉnh sửa (optional)
}