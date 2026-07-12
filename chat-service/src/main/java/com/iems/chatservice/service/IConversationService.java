package com.iems.chatservice.service;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;

import java.util.List;

public interface IConversationService {

    //Lay tin nhan cuoi cung cua cuoc hoi thoai cua user
    /**
     * Retrieves conversation information.
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     * @return the get last message for conversation result
     */
    Message getLastMessageForConversation(String conversationId, String accountId);

    //Tim hoi thoai bang id
    /**
     * Finds conversation information that matches the request.
     *
     * @param conversationId the conversation id parameter
     * @return the find by id result
     */
    Conversation findById(String conversationId);

    //Luu hoi thoai
    /**
     * Saves conversation data.
     *
     * @param conversation the conversation parameter
     * @return the save result
     */
    Conversation save(Conversation conversation);

    //Tim xem user do co trong hoi thoai nao
    /**
     * Finds conversation information that matches the request.
     *
     * @param accountId the account id parameter
     * @return the matching result collection
     */
    List<Conversation> findByMembersContaining(String accountId);

    //Ghim hoi thoai
    /**
     * Pins conversation data for quick access.
     *
     * @param conversationId the conversation id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    boolean pinConversation(String conversationId);

    //Bo ghim hoi thoai
    /**
     * Unpins conversation data.
     *
     * @param conversationId the conversation id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    boolean unpinConversation(String conversationId);

    //Danh dau cuoc hoi thoai la chua doc
    /**
     * Marks conversation data according to the request.
     *
     * @param conversationId the conversation id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    boolean markConversationAsUnread(String conversationId);

    //Tat bat thong bao
    /**
     * Returns toggle notification settings for conversation processing.
     *
     * @param conversationId the conversation id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    boolean toggleNotificationSettings(String conversationId);

    /**
     * Clears conversation state.
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    boolean clearManualUnreadMark(String conversationId, String accountId);
}
