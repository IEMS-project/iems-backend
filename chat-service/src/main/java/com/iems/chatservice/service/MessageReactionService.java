package com.iems.chatservice.service;

import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageReactionService {

    private final MongoTemplate mongoTemplate;
    private final MessageRepository messageRepository;
    private final MessageBroadcastService messageBroadcastService;

    public Message addReaction(String messageId, String userId, String emoji) {
        Query query = new Query(Criteria.where("id").is(messageId));
        Update update = new Update().addToSet("reactions." + emoji, userId);
        mongoTemplate.updateFirst(query, update, Message.class);

        Message updatedMessage = messageRepository.findById(messageId).orElse(null);
        if (updatedMessage != null) {
            messageBroadcastService.broadcastMessageUpdate(updatedMessage, "reaction_added", Map.of("userId", userId, "emoji", emoji));
        }
        return updatedMessage;
    }

    public Message removeReaction(String messageId, String userId) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return null;

        Map<String, List<String>> reactions = message.getReactions();
        if (reactions != null) {
            String removedEmoji = null;
            for (Map.Entry<String, List<String>> entry : reactions.entrySet()) {
                if (entry.getValue().contains(userId)) {
                    entry.getValue().remove(userId);
                    if (entry.getValue().isEmpty()) {
                        reactions.remove(entry.getKey());
                    }
                    removedEmoji = entry.getKey();
                    break;
                }
            }

            message.setReactions(reactions);
            Message saved = messageRepository.save(message);

            if (removedEmoji != null) {
                messageBroadcastService.broadcastMessageUpdate(saved, "reaction_removed", Map.of("userId", userId, "emoji", removedEmoji));
            }
            return saved;
        }
        return message;
    }
}


