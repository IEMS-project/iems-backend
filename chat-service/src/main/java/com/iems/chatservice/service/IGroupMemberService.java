package com.iems.chatservice.service;

import com.iems.chatservice.entity.Conversation;

public interface IGroupMemberService {
    //Them thanh vien
    Conversation addMember(String conversationId, String userId);

    //Xoa thanh vien
    Conversation removeMember(String conversationId, String userId);

    //Tao tin nhan he thong khi them xoa thanh vien
    void createAndBroadcastSystemLog(String conversationId, String content);

    void broadcastMemberEvent(Conversation conversation, String event, String targetUserId, String actorUserId);

}
