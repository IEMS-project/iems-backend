package com.iems.chatservice.service.Impl;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.ConversationRepository;
import com.iems.chatservice.repository.MessageRepository;
import com.iems.chatservice.service.IMessageBroadcastService;
import com.iems.chatservice.service.IMessageDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageDeletionService implements IMessageDeletionService {

    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private IMessageBroadcastService messageBroadcastService;

    /**
     * Deletes message deletion data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param messageId the message id parameter
     * @param accountId the account id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    @Override
    public boolean deleteForMe(String messageId, String accountId) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) {
            return false;
        }

        Conversation conversation = conversationRepository.findById(message.getConversationId()).orElse(null);
        if (conversation == null || !conversation.getMembers().contains(accountId)) {
            return false;
        }

        if (message.getDeletedForUsers() != null && message.getDeletedForUsers().contains(accountId)) {
            return true;
        }

        Query query = new Query(Criteria.where("id").is(messageId));
        Update update = new Update().addToSet("deletedForUsers", accountId);
        mongoTemplate.updateFirst(query, update, Message.class);

        try {
            Map<String, Object> payload = Map.of(
                "event", "message_deleted_for_user",
                "messageId", messageId,
                "accountId", accountId,
                "conversationId", message.getConversationId()
            );

            for (String memberId : conversation.getMembers()) {
                messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
            }

            Message latestVisible = messageBroadcastService.getLatestVisibleMessageForUser(message.getConversationId(), accountId);
            Map<String, Object> convUpdate = new HashMap<>();
            convUpdate.put("event", "conversation_updated");
            convUpdate.put("conversationId", message.getConversationId());
            if (latestVisible != null) {
                String lmContent = (Boolean.TRUE.equals(latestVisible.isRecalled())) ?
                        "Tin nhắn đã được thu hồi" : latestVisible.getContent();
                convUpdate.put("lastMessage", Map.of(
                        "id", latestVisible.getId(),
                        "content", lmContent,
                        "senderId", latestVisible.getSenderId(),
                        "sentAt", latestVisible.getSentAt(),
                        "type", latestVisible.getType()
                ));
                convUpdate.put("updatedAt", latestVisible.getSentAt());
            }
            messagingTemplate.convertAndSend("/topic/user-updates/" + accountId, convUpdate);
        } catch (Exception e) {
            System.err.println("Failed to broadcast delete event: " + e.getMessage());
        }

        return true;
    }

    /**
     * Returns recall message for message deletion processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param messageId the message id parameter
     * @param accountId the account id parameter
     * @return the recall message result
     * @throws RuntimeException if the service cannot complete the requested operation
     */
    @Override
    public Message recallMessage(String messageId, String accountId) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return null;

        if (!message.getSenderId().equals(accountId)) {
            throw new RuntimeException("Only sender can recall message");
        }

        message.setRecalled(true);
        message.setRecalledAt(LocalDateTime.now());
        Message saved = messageRepository.save(message);

        messageBroadcastService.broadcastMessageUpdate(saved, "message_recalled", Map.of("recalledBy", accountId));

        Conversation conv = conversationRepository.findById(saved.getConversationId()).orElse(null);
        if (conv != null) {
            messageBroadcastService.broadcastConversationUpdate(conv, saved);
        }

        return saved;
    }
}


