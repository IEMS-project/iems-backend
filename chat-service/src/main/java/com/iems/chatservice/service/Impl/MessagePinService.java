package com.iems.chatservice.service.Impl;

import com.iems.chatservice.entity.Message;
import com.iems.chatservice.repository.MessageRepository;
import com.iems.chatservice.service.IMessageBroadcastService;
import com.iems.chatservice.service.IMessagePinService;
import com.iems.chatservice.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessagePinService implements IMessagePinService {

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private IMessageBroadcastService messageBroadcastService;
    @Autowired
    private IUserService userService;

    private final String SYSTEM_SENDER = "SYSTEM";

    /**
     * Pins message pin data for quick access.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param messageId the message id parameter
     * @param accountId the account id parameter
     * @return the pin message result
     */
    @Override
    public Message pinMessage(String conversationId, String messageId, String accountId) {
        Query messageQuery = new Query(Criteria.where("id").is(messageId));
        Update messageUpdate = new Update()
                .set("pinned", true)
                .set("pinnedBy", accountId)
                .set("pinnedAt", java.time.LocalDateTime.now());
        mongoTemplate.updateFirst(messageQuery, messageUpdate, Message.class);

        Query convQuery = new Query(Criteria.where("id").is(conversationId));
        Update convUpdate = new Update().addToSet("pinnedMessageIds", messageId);
        mongoTemplate.updateFirst(convQuery, convUpdate, com.iems.chatservice.entity.Conversation.class);

        Message pinnedMessage = messageRepository.findById(messageId).orElse(null);
        if (pinnedMessage != null) {
            messageBroadcastService.broadcastMessageUpdate(pinnedMessage, "message_pinned", java.util.Map.of("pinnedBy", accountId));

            // create system log
            com.iems.chatservice.entity.Message log = new com.iems.chatservice.entity.Message();
            log.setConversationId(conversationId);
            log.setSenderId(SYSTEM_SENDER);
            log.setType("SYSTEM_LOG");
            log.setContent(String.format("%s đã ghim một tin nhắn", userService.resolveUserName(accountId)));
            messageBroadcastService.saveAndBroadcast(log);
        }
        return pinnedMessage;
    }

    /**
     * Unpins message pin data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param messageId the message id parameter
     * @param accountId the account id parameter
     * @return the unpin message result
     */
    @Override
    public Message unpinMessage(String conversationId, String messageId, String accountId) {
        Query messageQuery = new Query(Criteria.where("id").is(messageId));
        Update messageUpdate = new Update()
                .set("pinned", false)
                .unset("pinnedBy")
                .unset("pinnedAt");
        mongoTemplate.updateFirst(messageQuery, messageUpdate, Message.class);

        Query convQuery = new Query(Criteria.where("id").is(conversationId));
        Update convUpdate = new Update().pull("pinnedMessageIds", messageId);
        mongoTemplate.updateFirst(convQuery, convUpdate, com.iems.chatservice.entity.Conversation.class);

        Message unpinnedMessage = messageRepository.findById(messageId).orElse(null);
        if (unpinnedMessage != null) {
            messageBroadcastService.broadcastMessageUpdate(unpinnedMessage, "message_unpinned", java.util.Map.of("unpinnedBy", accountId));

            // create system log
            com.iems.chatservice.entity.Message log = new com.iems.chatservice.entity.Message();
            log.setConversationId(conversationId);
            log.setSenderId(SYSTEM_SENDER);
            log.setType("SYSTEM_LOG");
            log.setContent(String.format("%s đã bỏ ghim một tin nhắn", userService.resolveUserName(accountId)));
            messageBroadcastService.saveAndBroadcast(log);
        }
        return unpinnedMessage;
    }


}


