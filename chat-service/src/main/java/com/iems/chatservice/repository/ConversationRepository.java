package com.iems.chatservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import com.iems.chatservice.entity.Conversation;

import java.util.List;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<Conversation> findByMembersContaining(String userId);

    List<Conversation> findByTypeAndMembersIn(String type, List<String> members);

    // Find DIRECT conversation that contains both members (Mongo $all)
    @Query(value = "{ 'type': ?0, 'members': { $all: ?1 } }")
    List<Conversation> findByTypeAndMembersAll(String type, List<String> members);
}



