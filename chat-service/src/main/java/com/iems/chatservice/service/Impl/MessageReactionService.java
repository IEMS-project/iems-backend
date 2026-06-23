package com.iems.chatservice.service.Impl;

import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.MessageRepository;
import com.iems.chatservice.service.IMessageBroadcastService;
import com.iems.chatservice.service.IMessageReactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageReactionService implements IMessageReactionService {

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private IMessageBroadcastService messageBroadcastService;

    @Override
    public Message addReaction(String messageId, String accountId, String emoji) {
        Query query = new Query(Criteria.where("id").is(messageId));
        Update update = new Update().addToSet("reactions." + emoji, accountId);
        mongoTemplate.updateFirst(query, update, Message.class);

        Message updatedMessage = messageRepository.findById(messageId).orElse(null);
        if (updatedMessage != null) {
            messageBroadcastService.broadcastMessageUpdate(updatedMessage, "reaction_added", Map.of("accountId", accountId, "emoji", emoji));
        }
        return updatedMessage;
    }

    @Override
    public Message removeReaction(String messageId, String accountId) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return null;

        Map<String, List<String>> reactions = message.getReactions();
        if (reactions != null) {
            String removedEmoji = null;
            for (Map.Entry<String, List<String>> entry : reactions.entrySet()) {
                if (entry.getValue().contains(accountId)) {
                    entry.getValue().remove(accountId);
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
                messageBroadcastService.broadcastMessageUpdate(saved, "reaction_removed", Map.of("accountId", accountId, "emoji", removedEmoji));
            }
            return saved;
        }
        return message;
    }
}


