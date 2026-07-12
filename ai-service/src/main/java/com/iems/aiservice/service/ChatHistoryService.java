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

    /**
     * Creates a new chat history service instance.
     *
     * @param conversationRepository the conversation repository parameter
     * @param chatMessageRepository the chat message repository parameter
     */
    public ChatHistoryService(ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Ensures that chat history requirements are satisfied.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param id the id parameter
     * @param userId the user id parameter
     * @param firstMessage the first message parameter
     * @param projectId the project id parameter
     * @return the ensure conversation result
     */
    public String ensureConversation(String id, String userId, String firstMessage, String projectId) {
        if (id != null && !id.isBlank() && conversationRepository.existsById(id)) {
            updateTimestamp(id);
            return id;
        }
        return createNew(UUID.randomUUID().toString(), userId, firstMessage, projectId).getId();
    }

    /**
     * Creates chat history data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param newId the new id parameter
     * @param userId the user id parameter
     * @param firstMessage the first message parameter
     * @param projectId the project id parameter
     * @return the create new result
     */
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

    /**
     * Updates chat history data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param id the id parameter
     */
    public void updateTimestamp(String id) {
        conversationRepository.findById(id).ifPresent(c -> {
            c.setUpdatedAt(Instant.now());
            conversationRepository.save(c);
        });
    }

    /**
     * Saves chat history data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param role the role parameter
     * @param content the content parameter
     * @return the save message result
     */
    public ChatMessage saveMessage(String conversationId, String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setId(UUID.randomUUID().toString());
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTimestamp(Instant.now());
        return chatMessageRepository.save(msg);
    }

    /**
     * Retrieves chat history information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @return the matching result collection
     */
    public List<Conversation> getUserConversations(String userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /**
     * Retrieves chat history information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @return the matching result collection
     */
    public List<ChatMessage> getConversationMessages(String conversationId) {
        return chatMessageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
    }

    /**
     * Builds chat history data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @return the build recent conversation context result
     */
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

    /**
     * Deletes chat history data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param id the id parameter
     */
    public void deleteConversation(String id) {
        conversationRepository.deleteById(id);
        chatMessageRepository.deleteByConversationId(id);
    }

    /**
     * Returns rename conversation for chat history processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param id the id parameter
     * @param newName the new name parameter
     * @return the rename conversation result
     */
    public Conversation renameConversation(String id, String newName) {
        return conversationRepository.findById(id).map(c -> {
            c.setName(newName);
            c.setUpdatedAt(Instant.now());
            return conversationRepository.save(c);
        }).orElse(null);
    }

    /**
     * Clears chat history state.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param userId the user id parameter
     */
    public void clearAllMemory(String userId) {
        List<Conversation> conversations = getUserConversations(userId);
        for (Conversation c : conversations) {
            deleteConversation(c.getId());
        }
    }
}