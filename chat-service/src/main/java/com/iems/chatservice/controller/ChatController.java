package com.iems.chatservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.service.MessageService;
import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.repository.ConversationRepository;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final MessageService messageService;
    private final MongoTemplate mongoTemplate;
    private final ConversationRepository conversationRepository;

    // Send to a conversation (group topic)
    @MessageMapping("/conversations/{conversationId}/send")
    @SendTo("/topic/conversations/{conversationId}")
    public Message sendToConversation(@DestinationVariable String conversationId, @Payload Message message) {
        message.setConversationId(conversationId);
        return messageService.saveAndBroadcast(message);
    }

    // Send 1-1 message using the same mapping (service will route per members)
    @MessageMapping("/messages/send")
    public void sendDirect(@Payload Message message) {
        // Ensure DIRECT conversation exists for the two users on first message
        if (message.getConversationId() == null || message.getConversationId().isBlank()) {
            // Require senderId and a recipientId in content? For simplicity, assume conversationId provided.
            messageService.saveAndBroadcast(message);
            return;
        }
        Conversation conv = mongoTemplate.findById(message.getConversationId(), Conversation.class);
        if (conv == null) {
            // Cannot infer members here without payload. Fall back to save message with given conversationId
            messageService.saveAndBroadcast(message);
            return;
        }
        if ("DIRECT".equalsIgnoreCase(conv.getType())) {
            // ok - this is a direct conversation
        }
        messageService.saveAndBroadcast(message);
    }

    // Handle replying events (start/stop replying indicators)
    @MessageMapping("/conversations/{conversationId}/replying")
    @SendTo("/topic/conversations/{conversationId}")
    public Map<String, Object> handleReplyEvent(@DestinationVariable String conversationId, @Payload Map<String, Object> payload) {
        // Simply broadcast the reply event to all participants in the conversation
        return payload;
    }

    // Handle reply message via WebSocket
    @MessageMapping("/conversations/{conversationId}/reply")
    public void replyToMessage(@DestinationVariable String conversationId, @Payload Map<String, Object> payload) {
        String content = (String) payload.get("content");
        String replyToMessageId = (String) payload.get("replyToMessageId");
        
        if (content != null && replyToMessageId != null) {
            messageService.replyToMessage(conversationId, content, replyToMessageId);
        }
    }

    // Handle reaction events via WebSocket
    @MessageMapping("/conversations/{conversationId}/reaction")
    public void handleReaction(@DestinationVariable String conversationId, @Payload Map<String, Object> payload) {
        String messageId = (String) payload.get("messageId");
        String emoji = (String) payload.get("emoji");
        String action = (String) payload.get("action"); // "add" or "remove"
        
        if (messageId != null) {
            if ("add".equals(action) && emoji != null) {
                messageService.addReaction(messageId, emoji);
            } else if ("remove".equals(action)) {
                messageService.removeReaction(messageId);
            }
        }
    }

    // Handle message delete/recall events via WebSocket
    @MessageMapping("/conversations/{conversationId}/delete")
    public void handleMessageDelete(@DestinationVariable String conversationId, @Payload Map<String, Object> payload) {
        String messageId = (String) payload.get("messageId");
        String action = (String) payload.get("action"); // "delete_for_me" or "recall"
        
        if (messageId != null && action != null) {
            if ("delete_for_me".equals(action)) {
                messageService.deleteForMe(messageId);
            } else if ("recall".equals(action)) {
                messageService.recallMessage(messageId);
            }
        }
    }

    // Handle pin/unpin events via WebSocket
    @MessageMapping("/conversations/{conversationId}/pin")
    public void handleMessagePin(@DestinationVariable String conversationId, @Payload Map<String, Object> payload) {
        String messageId = (String) payload.get("messageId");
        String action = (String) payload.get("action"); // "pin" or "unpin"
        
        if (messageId != null && action != null) {
            if ("pin".equals(action)) {
                messageService.pinMessage(conversationId, messageId);
            } else if ("unpin".equals(action)) {
                messageService.unpinMessage(conversationId, messageId);
            }
        }
    }

    // Handle read status events via WebSocket
    @MessageMapping("/conversations/{conversationId}/read")
    public void handleReadStatus(@DestinationVariable String conversationId, @Payload Map<String, Object> payload) {
        String lastMessageId = (String) payload.get("lastMessageId");
        
        if (lastMessageId != null) {
            messageService.markAsReadWithLastMessage(conversationId, lastMessageId);
        } else {
            messageService.markAsRead(conversationId);
        }
    }

    // Handle typing indicator events
    @MessageMapping("/conversations/{conversationId}/typing")
    @SendTo("/topic/conversations/{conversationId}")
    public Map<String, Object> handleTypingIndicator(@DestinationVariable String conversationId, @Payload Map<String, Object> payload) {
        // Add event type for client handling
        payload.put("event", "typing");
        return payload;
    }

    // Handle mark conversation as read via WebSocket
    @MessageMapping("/conversations/{conversationId}/mark-read")
    public void handleMarkConversationAsRead(@DestinationVariable String conversationId, @Payload Map<String, Object> payload) {
        messageService.markConversationAsRead(conversationId);
    }
}



