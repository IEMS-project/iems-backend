package com.iems.chatservice.service;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;

import java.util.List;

public interface IConversationService {

    //Lay tin nhan cuoi cung cua cuoc hoi thoai cua user
    Message getLastMessageForConversation(String conversationId, String accountId);

    //Tim hoi thoai bang id
    Conversation findById(String conversationId);

    //Luu hoi thoai
    Conversation save(Conversation conversation);

    //Tim xem user do co trong hoi thoai nao
    List<Conversation> findByMembersContaining(String accountId);

    //Ghim hoi thoai
    boolean pinConversation(String conversationId);

    //Bo ghim hoi thoai
    boolean unpinConversation(String conversationId);

    //Danh dau cuoc hoi thoai la chua doc
    boolean markConversationAsUnread(String conversationId);

    //Tat bat thong bao
    boolean toggleNotificationSettings(String conversationId);

    boolean clearManualUnreadMark(String conversationId, String accountId);
}
