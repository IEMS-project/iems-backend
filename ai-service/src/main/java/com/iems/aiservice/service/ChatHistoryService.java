package com.iems.aiservice.service;

import com.iems.aiservice.entity.ChatMessage;
import com.iems.aiservice.entity.Conversation;
import com.iems.aiservice.repository.ChatMessageRepository;
import com.iems.aiservice.repository.ConversationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatHistoryService {

    private static final int DEFAULT_HISTORY_LIMIT = 10;
    private static final int MAX_HISTORY_CHARS = 4000;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatHistoryService(ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    public String ensureConversation(String id, String userId, String firstMessage, String projectId) {
        if (id != null && !id.isBlank() && conversationRepository.existsById(id)) {
            updateTimestamp(id);
            return id;
        }
        return createNew(UUID.randomUUID().toString(), userId, firstMessage, projectId).getId();
    }

    private Conversation createNew(String newId, String userId, String firstMessage, String projectId) {
        Conversation conv = new Conversation();
        conv.setId(newId);
        conv.setUserId(userId);
        conv.setProjectId(projectId);

        String cleanMessage = firstMessage != null ? firstMessage.trim() : "";
        String name = cleanMessage.length() > 60 ? cleanMessage.substring(0, 60) + "..." : cleanMessage;
        if (name.isEmpty()) {
            name = "New Conversation";
        }

        conv.setName(name);
        conv.setCreatedAt(Instant.now());
        conv.setUpdatedAt(Instant.now());
        return conversationRepository.save(conv);
    }

    public void updateTimestamp(String id) {
        conversationRepository.findById(id).ifPresent(c -> {
            c.setUpdatedAt(Instant.now());
            conversationRepository.save(c);
        });
    }

    public ChatMessage saveMessage(String conversationId, String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setId(UUID.randomUUID().toString());
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTimestamp(Instant.now());
        return chatMessageRepository.save(msg);
    }

    public List<Conversation> getUserConversations(String userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public List<ChatMessage> getConversationMessages(String conversationId) {
        return chatMessageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
    }

    public String buildRecentConversationContext(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "";
        }

        List<ChatMessage> messages = getConversationMessages(conversationId);
        if (messages.isEmpty()) {
            return "";
        }

        int fromIndex = Math.max(0, messages.size() - DEFAULT_HISTORY_LIMIT);
        String raw = messages.subList(fromIndex, messages.size()).stream()
                .map(message -> {
                    String roleLabel = "assistant".equalsIgnoreCase(message.getRole()) ? "Assistant" : "User";
                    String content = message.getContent() == null ? "" : message.getContent().trim();
                    return roleLabel + ": " + content;
                })
                .collect(Collectors.joining("\n"));

        if (raw.length() <= MAX_HISTORY_CHARS) {
            return raw;
        }
        return raw.substring(raw.length() - MAX_HISTORY_CHARS);
    }

    public void deleteConversation(String id) {
        conversationRepository.deleteById(id);
        chatMessageRepository.deleteByConversationId(id);
    }

    public Conversation renameConversation(String id, String newName) {
        return conversationRepository.findById(id).map(c -> {
            c.setName(newName);
            c.setUpdatedAt(Instant.now());
            return conversationRepository.save(c);
        }).orElse(null);
    }

    public void clearAllMemory(String userId) {
        List<Conversation> conversations = getUserConversations(userId);
        for (Conversation c : conversations) {
            deleteConversation(c.getId());
        }
    }
}