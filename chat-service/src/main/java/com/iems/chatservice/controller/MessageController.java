package com.iems.chatservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.service.MessageService;
import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.repository.ConversationRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final MongoTemplate mongoTemplate;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ResponseEntity<Page<Message>> recent(
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(messageService.getRecentMessages(conversationId, page, size));
    }

    @GetMapping("/scroll")
    public ResponseEntity<Map<String, Object>> scroll(
            @RequestParam String conversationId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "20") int limit
    ) {
        LocalDateTime beforeTs = null;
        if (before != null && !before.isBlank()) {
            try { beforeTs = LocalDateTime.parse(before); } catch (Exception ignore) { }
        }
        List<Message> list = messageService.getMessagesScroll(conversationId, beforeTs, limit);
        String nextCursor = null;
        if (list != null && !list.isEmpty()) {
            Message last = list.get(list.size() - 1);
            nextCursor = last.getSentAt() != null ? last.getSentAt().toString() : null;
        }
        // Return messages in ascending order for UI convenience
        List<Message> ascending = new java.util.ArrayList<>(list);
        java.util.Collections.reverse(ascending);
        return ResponseEntity.ok(java.util.Map.of(
                "messages", ascending,
                "nextCursor", nextCursor
        ));
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(
            @RequestParam String conversationId,
            @RequestParam(required = false) String lastMessageId
    ) {
        if (lastMessageId != null && !lastMessageId.isBlank()) {
            messageService.markAsReadWithLastMessage(conversationId, lastMessageId);
        } else {
            messageService.markAsRead(conversationId);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread")
    public ResponseEntity<Map<String, Integer>> unreadCounts() {
        return ResponseEntity.ok(messageService.getUnreadCountsByUser());
    }

    // Reply to message
    @PostMapping("/reply")
    public ResponseEntity<Message> replyToMessage(
            @RequestParam String conversationId,
            @RequestParam String content,
            @RequestParam String replyToMessageId
    ) {
        Message reply = messageService.replyToMessage(conversationId, content, replyToMessageId);
        return ResponseEntity.ok(reply);
    }

    // Add reaction to message
    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<Message> addReaction(
            @PathVariable String messageId,
            @RequestParam String emoji 
    ) {
        Message message = messageService.addReaction(messageId, emoji);
        return ResponseEntity.ok(message);
    }

    // Remove reaction from message
    @DeleteMapping("/{messageId}/reactions")
    public ResponseEntity<Message> removeReaction(
            @PathVariable String messageId
    ) {
        Message message = messageService.removeReaction(messageId);
        return ResponseEntity.ok(message);
    }

    // Delete message for me
    @PostMapping("/{messageId}/delete")
    public ResponseEntity<Void> deleteForMe(
            @PathVariable String messageId
    ) {
        boolean success = messageService.deleteForMe(messageId);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Alternative endpoint with DELETE method
    @DeleteMapping("/{messageId}/me")
    public ResponseEntity<Void> deleteForMeAlt(
            @PathVariable String messageId
    ) {
        boolean success = messageService.deleteForMe(messageId);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Recall message (delete for everyone)
    @PostMapping("/{messageId}/recall")
    public ResponseEntity<Message> recallMessage(
            @PathVariable String messageId
    ) {
        Message message = messageService.recallMessage(messageId);
        return ResponseEntity.ok(message);
    }

    // Get pinned messages for conversation
    @GetMapping("/pinned")
    public ResponseEntity<List<Message>> getPinnedMessages(@RequestParam String conversationId) {
        List<Message> pinnedMessages = messageService.getPinnedMessages(conversationId);
        return ResponseEntity.ok(pinnedMessages);
    }

    // Get messages for specific user (with filtering)
    @GetMapping("/for-user")
    public ResponseEntity<List<Message>> getMessagesForUser(
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        List<Message> messages = messageService.getMessagesForUser(conversationId, page, size);
        return ResponseEntity.ok(messages);
    }

    // Paginated messages for conversation
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> getConversationMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String before
    ) {
        Map<String, Object> result = messageService.getConversationMessagesPaginated(conversationId, limit, before);
        return ResponseEntity.ok(result);
    }

    // Get message by ID with neighbors for jump-to-message functionality
    @GetMapping("/{messageId}")
    public ResponseEntity<Map<String, Object>> getMessageWithNeighbors(
            @PathVariable String messageId,
            @RequestParam(required = false, defaultValue = "true") boolean withNeighbors,
            @RequestParam(required = false, defaultValue = "10") int neighborLimit
    ) {
        Map<String, Object> result = messageService.getMessageWithNeighbors(messageId, withNeighbors, neighborLimit);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    // Search messages by text content
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchMessages(
            @RequestParam String conversationId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Map<String, Object> result = messageService.searchMessages(conversationId, keyword, page, size);
        return ResponseEntity.ok(result);
    }

    // Get messages around a specific message (for jump-to-message)
    @GetMapping("/around/{messageId}")
    public ResponseEntity<Map<String, Object>> getMessagesAround(
            @PathVariable String messageId,
            @RequestParam(required = false, defaultValue = "5") int before,
            @RequestParam(required = false, defaultValue = "5") int after
    ) {
        Map<String, Object> result = messageService.getMessagesAround(messageId, before, after);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    // Get messages between two message IDs (for gap filling)
    @GetMapping("/between")
    public ResponseEntity<List<Message>> getMessagesBetween(
            @RequestParam String fromMessageId,
            @RequestParam String toMessageId,
            @RequestParam String conversationId
    ) {
        List<Message> messages = messageService.getMessagesBetween(fromMessageId, toMessageId, conversationId);
        return ResponseEntity.ok(messages);
    }

    // Send first DIRECT message; create conversation if absent
    @PostMapping("/direct")
    public ResponseEntity<Message> sendDirect(
            @RequestParam String senderId,
            @RequestParam String recipientId,
            @RequestParam String content
    ) {
        if (senderId == null || recipientId == null || senderId.isBlank() || recipientId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("type").is("DIRECT"));
        query.addCriteria(Criteria.where("members").all(Arrays.asList(senderId, recipientId)));
        Conversation conv = mongoTemplate.findOne(query, Conversation.class);
        boolean created = false;
        if (conv == null) {
            conv = new Conversation();
            conv.setType("DIRECT");
            conv.setName(null);
            conv.setMembers(Arrays.asList(senderId, recipientId));
            conv = conversationRepository.save(conv);
            created = true;
        }
        Message msg = new Message();
        msg.setConversationId(conv.getId());
        msg.setSenderId(senderId);
        msg.setContent(content);
        Message saved = messageService.saveAndBroadcast(msg);
        if (created) {
            // notify both users to refresh conversation list
            try {
                var payload = java.util.Map.of(
                        "event", "conversation_created",
                        "conversationId", conv.getId(),
                        "type", conv.getType(),
                        "members", conv.getMembers(),
                        "preview", content
                );
                messagingTemplate.convertAndSend("/topic/user-updates/" + senderId, payload);
                messagingTemplate.convertAndSend("/topic/user-updates/" + recipientId, payload);
            } catch (Exception ignore) { }
        }
        return ResponseEntity.ok(saved);
    }
}


