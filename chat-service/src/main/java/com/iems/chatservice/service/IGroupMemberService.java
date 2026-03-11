package com.iems.chatservice.service;

import com.iems.chatservice.entity.Conversation;

public interface IGroupMemberService {
    //Them thanh vien
    Conversation addMember(String conversationId, String accountId);

    //Xoa thanh vien
    Conversation removeMember(String conversationId, String accountId);

    //Tao tin nhan he thong khi them xoa thanh vien
    void createAndBroadcastSystemLog(String conversationId, String content);

    void broadcastMemberEvent(Conversation conversation, String event, String targetAccountId, String actorAccountId);

}
