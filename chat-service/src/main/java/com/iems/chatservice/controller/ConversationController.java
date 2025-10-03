package com.iems.chatservice.controller;

import com.iems.chatservice.dto.MemberResponseDto;
import com.iems.chatservice.service.MessageService;
import com.iems.chatservice.service.ConversationService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.repository.ConversationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final MongoTemplate mongoTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageService messageService;
    
    @Autowired
    private ConversationService conversationService;

    @PostMapping
    public ResponseEntity<Conversation> create(@RequestBody Conversation conversation) {
        Conversation saved = conversationRepository.save(conversation);
        try {
            List<String> members = saved.getMembers();
            if (members != null) {
                var payload = java.util.Map.of(
                        "event", "conversation_created",
                        "conversationId", saved.getId(),
                        "type", saved.getType(),
                        "members", members,
                        "preview", "Cuộc trò chuyện mới: " + (saved.getName() == null ? "Group" : saved.getName())
                );
                for (String memberId : members) {
                    messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
                }
            }
        } catch (Exception ignore) { }
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<MemberResponseDto>> getMembers(@PathVariable String id) {
        return ResponseEntity.ok(messageService.getMembersByConversationId(id));
    }
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Map<String, Object>>> byUser(@PathVariable String userId) {
        return ResponseEntity.ok(conversationService.getConversationsByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Conversation> getById(@PathVariable String id) {
        return conversationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/members/{userId}")
    public ResponseEntity<Conversation> addMember(@PathVariable String id, @PathVariable String userId) {
        return conversationRepository.findById(id)
                .map(conv -> {
                    List<String> members = conv.getMembers();
                    if (members == null) {
                        members = new ArrayList<>();
                        conv.setMembers(members);
                    }
                    if (!members.contains(userId)) {
                        members.add(userId);
                    }
                    Conversation updated = conversationRepository.save(conv);
                    try {
                        var payload = java.util.Map.of(
                                "event", "conversation_created",
                                "conversationId", updated.getId(),
                                "type", updated.getType(),
                                "members", updated.getMembers(),
                                "preview", "Bạn vừa được thêm vào nhóm"
                        );
                        messagingTemplate.convertAndSend("/topic/user-updates/" + userId, payload);
                    } catch (Exception ignore) { }
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Get or create a DIRECT conversation between two users (supports both POST and GET)
    @PostMapping("/direct")
    public ResponseEntity<Conversation> directPost(@RequestParam String userA, @RequestParam String userB) {
        return resolveDirect(userA, userB);
    }

    @GetMapping("/direct")
    public ResponseEntity<Conversation> directGet(@RequestParam String userA, @RequestParam String userB) {
        return resolveDirect(userA, userB);
    }

    private ResponseEntity<Conversation> resolveDirect(String userA, String userB) {
        if (userA == null || userB == null || userA.isBlank() || userB.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        // Use MongoTemplate with $all to avoid multiple 'members' criteria collision
        Query query = new Query();
        query.addCriteria(Criteria.where("type").is("DIRECT"));
        query.addCriteria(Criteria.where("members").all(Arrays.asList(userA, userB)));
        List<Conversation> existing = mongoTemplate.find(query, Conversation.class);
        if (!existing.isEmpty()) {
            return ResponseEntity.ok(existing.get(0));
        }
        // Do not create here. Only create on first message send.
        return ResponseEntity.notFound().build();
    }

    // Pin message in conversation
    @PostMapping("/{conversationId}/pin")
    public ResponseEntity<com.iems.chatservice.entity.Message> pinMessage(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> request
    ) {
        String messageId = request.get("messageId");
        String userId = request.get("userId");
        if (messageId == null || userId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        com.iems.chatservice.entity.Message pinnedMessage = messageService.pinMessage(conversationId, messageId, userId);
        return ResponseEntity.ok(pinnedMessage);
    }

    // Unpin message in conversation
    @PostMapping("/{conversationId}/unpin")
    public ResponseEntity<com.iems.chatservice.entity.Message> unpinMessage(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> request
    ) {
        String messageId = request.get("messageId");
        String userId = request.get("userId");
        if (messageId == null || userId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        com.iems.chatservice.entity.Message unpinnedMessage = messageService.unpinMessage(conversationId, messageId, userId);
        return ResponseEntity.ok(unpinnedMessage);
    }

    // Mark all messages in conversation as read
    @PostMapping("/{conversationId}/mark-read")
    public ResponseEntity<Void> markConversationAsRead(
            @PathVariable String conversationId,
            @RequestParam String userId
    ) {
        boolean success = messageService.markConversationAsRead(conversationId, userId);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    // Get unread count for all conversations of a user
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(@RequestParam String userId) {
        Map<String, Integer> unreadCounts = messageService.getUnreadCountsByUser(userId);
        return ResponseEntity.ok(unreadCounts);
    }
}



