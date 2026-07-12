package com.iems.chatservice.service;

import com.iems.chatservice.entity.Conversation;

public interface IGroupMemberService {
    //Them thanh vien
    /**
     * Adds group member data for the request.
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     * @return the add member result
     */
    Conversation addMember(String conversationId, String accountId);

    //Xoa thanh vien
    /**
     * Removes group member data for the request.
     *
     * @param conversationId the conversation id parameter
     * @param accountId the account id parameter
     * @return the remove member result
     */
    Conversation removeMember(String conversationId, String accountId);

    //Tao tin nhan he thong khi them xoa thanh vien
    /**
     * Creates group member data for the request.
     *
     * @param conversationId the conversation id parameter
     * @param content the content parameter
     */
    void createAndBroadcastSystemLog(String conversationId, String content);

    /**
     * Performs broadcast member event for group member processing.
     *
     * @param conversation the conversation parameter
     * @param event the event parameter
     * @param targetAccountId the target account id parameter
     * @param actorAccountId the actor account id parameter
     */
    void broadcastMemberEvent(Conversation conversation, String event, String targetAccountId, String actorAccountId);

}
