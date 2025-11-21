package com.iems.chatservice.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    //Xem tin nhan duoc doc cuoi cung cua moi nguoi
    private Map<String, String> lastReadMessageId; // userId -> messageId

    //Danh sach nhung tin nhan duoc ghim
    private List<String> pinnedMessageIds;

    //Thong tin cuoc hoi thoai
    private String createdBy; //Truong nhom
    private String avatarUrl;
    
    //Nhung nguoi ghim cuoc hoi thoai
    private Map<String, LocalDateTime> pinnedBy;
    
    //Ai bat tat thong bao
    private Map<String, Boolean> notificationSettings;
    
    //Danh dau chua doc
    private Set<String> manuallyMarkedAsUnread;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();
}
