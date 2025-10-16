package com.iems.chatservice.controller;

import com.iems.chatservice.dto.MemberResponseDto;
import com.iems.chatservice.service.MessageService;
import com.iems.chatservice.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.repository.ConversationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final MongoTemplate mongoTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private com.iems.chatservice.service.GroupMemberService groupMemberService;
    
    @Autowired
    private com.iems.chatservice.service.MessageBroadcastService messageBroadcastService;
    
    @Autowired
    private com.iems.chatservice.client.UserServiceFeignClient userServiceFeignClient;

    @PostMapping
    public ResponseEntity<Conversation> create(@RequestBody Conversation conversation,
                                               @RequestParam(required = false) String actorUserId) {
        // Resolve actor user id from request param or security context
        String finalActorUserId;
        if (actorUserId == null || actorUserId.isBlank()) {
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof com.iems.chatservice.security.JwtUserDetails jwt) {
                    finalActorUserId = jwt.getUserId().toString();
                } else {
                    finalActorUserId = null;
                }
            } catch (Exception ignore) { 
                finalActorUserId = null;
            }
        } else {
            finalActorUserId = actorUserId;
        }

        // Ensure createdBy is set to actor
        try {
            if (conversation.getCreatedBy() == null || conversation.getCreatedBy().isBlank()) {
                conversation.setCreatedBy(finalActorUserId);
            }
        } catch (Exception ignore) { }

        // Ensure creator is in members list for GROUP
        try {
            if ("GROUP".equalsIgnoreCase(conversation.getType())) {
                List<String> members = conversation.getMembers();
                if (members == null) {
                    members = new ArrayList<>();
                    conversation.setMembers(members);
                }
                if (finalActorUserId != null && !finalActorUserId.isBlank() && !members.contains(finalActorUserId)) {
                    members.add(finalActorUserId);
                }
            }
        } catch (Exception ignore) { }

        Conversation saved = conversationRepository.save(conversation);
        
        // Create system messages for GROUP conversations
        if ("GROUP".equalsIgnoreCase(saved.getType())) {
            try {
                // Create "group created" system message
                com.iems.chatservice.entity.Message groupCreatedMsg = new com.iems.chatservice.entity.Message();
                groupCreatedMsg.setConversationId(saved.getId());
                groupCreatedMsg.setSenderId("SYSTEM");
                groupCreatedMsg.setType("SYSTEM_LOG");
                groupCreatedMsg.setContent("Nhóm đã được tạo");
                groupCreatedMsg.setSentAt(java.time.LocalDateTime.now());
                messageBroadcastService.saveAndBroadcast(groupCreatedMsg);
                
                // Create "members added" system message for each member (except creator)
                List<String> members = saved.getMembers();
                if (members != null && members.size() > 1) {
                    final String creatorId = finalActorUserId; // Create final reference
                    List<String> resolvedNames = new ArrayList<>();
                    for (String memberId : members) {
                        if (!memberId.equals(creatorId)) {
                            String resolvedName = resolveUserName(memberId);
                            // Only add if name was actually resolved (not just the ID)
                            if (!resolvedName.equals(memberId)) {
                                resolvedNames.add(resolvedName);
                            }
                        }
                    }
                    
                    if (!resolvedNames.isEmpty()) {
                        String memberNames = String.join(", ", resolvedNames);
                        com.iems.chatservice.entity.Message membersAddedMsg = new com.iems.chatservice.entity.Message();
                        membersAddedMsg.setConversationId(saved.getId());
                        membersAddedMsg.setSenderId("SYSTEM");
                        membersAddedMsg.setType("SYSTEM_LOG");
                        membersAddedMsg.setContent(memberNames + " đã được thêm vào nhóm");
                        membersAddedMsg.setSentAt(java.time.LocalDateTime.now());
                        messageBroadcastService.saveAndBroadcast(membersAddedMsg);
                    } else {
                        // If no proper names found, just say "các thành viên"
                        com.iems.chatservice.entity.Message membersAddedMsg = new com.iems.chatservice.entity.Message();
                        membersAddedMsg.setConversationId(saved.getId());
                        membersAddedMsg.setSenderId("SYSTEM");
                        membersAddedMsg.setType("SYSTEM_LOG");
                        membersAddedMsg.setContent("Các thành viên đã được thêm vào nhóm");
                        membersAddedMsg.setSentAt(java.time.LocalDateTime.now());
                        messageBroadcastService.saveAndBroadcast(membersAddedMsg);
                    }
                }
            } catch (Exception ignore) { }
        }
        
        try {
            List<String> members = saved.getMembers();
            if (members != null) {
                var payload = java.util.Map.of(
                        "event", "conversation_created",
                        "conversationId", saved.getId(),
                        "type", saved.getType(),
                        "members", members,
                        "preview", "Cuộc trò chuyện mới: " + (saved.getName() == null ? "Group" : saved.getName())
                );
                for (String memberId : members) {
                    messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
                }
            }
        } catch (Exception ignore) { }
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<MemberResponseDto>> getMembers(@PathVariable String id) {
        return ResponseEntity.ok(messageService.getMembersByConversationId(id));
    }
    @GetMapping("/user")
    public ResponseEntity<List<Map<String, Object>>> byUser() {
        return ResponseEntity.ok(conversationService.getConversationsByUser());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Conversation> getById(@PathVariable String id) {
        return conversationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/members/{userId}")
    public ResponseEntity<Conversation> addMember(@PathVariable String id, @PathVariable String userId) {
        Conversation result = groupMemberService.addMember(id, userId);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Conversation> removeMember(@PathVariable String id, @PathVariable String userId) {
        Conversation result = groupMemberService.removeMember(id, userId);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    // Get or create a DIRECT conversation between two users (supports both POST and GET)
    @PostMapping("/direct")
    public ResponseEntity<Conversation> directPost(@RequestParam String userA, @RequestParam String userB) {
        return resolveDirect(userA, userB);
    }

    @GetMapping("/direct")
    public ResponseEntity<Conversation> directGet(@RequestParam String userA, @RequestParam String userB) {
        return resolveDirect(userA, userB);
    }

    private ResponseEntity<Conversation> resolveDirect(String userA, String userB) {
        if (userA == null || userB == null || userA.isBlank() || userB.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        // Use MongoTemplate with $all to avoid multiple 'members' criteria collision
        Query query = new Query();
        query.addCriteria(Criteria.where("type").is("DIRECT"));
        query.addCriteria(Criteria.where("members").all(Arrays.asList(userA, userB)));
        List<Conversation> existing = mongoTemplate.find(query, Conversation.class);
        if (!existing.isEmpty()) {
            return ResponseEntity.ok(existing.get(0));
        }
        // Do not create here. Only create on first message send.
        return ResponseEntity.notFound().build();
    }

    // Pin message in conversation
    @PostMapping("/{conversationId}/pin")
    public ResponseEntity<com.iems.chatservice.entity.Message> pinMessage(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> request
    ) {
        String messageId = request.get("messageId");
        if (messageId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        com.iems.chatservice.entity.Message pinnedMessage = messageService.pinMessage(conversationId, messageId);
        return ResponseEntity.ok(pinnedMessage);
    }

    // Unpin message in conversation
    @PostMapping("/{conversationId}/unpin")
    public ResponseEntity<com.iems.chatservice.entity.Message> unpinMessage(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> request
    ) {
        String messageId = request.get("messageId");
        if (messageId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        com.iems.chatservice.entity.Message unpinnedMessage = messageService.unpinMessage(conversationId, messageId);
        return ResponseEntity.ok(unpinnedMessage);
    }

    // Mark all messages in conversation as read
    @PostMapping("/{conversationId}/mark-read")
    public ResponseEntity<Void> markConversationAsRead(
            @PathVariable String conversationId
    ) {
        boolean success = messageService.markConversationAsRead(conversationId);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    // Get unread count for all conversations of a user
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount() {
        Map<String, Integer> unreadCounts = messageService.getUnreadCountsByUser();
        return ResponseEntity.ok(unreadCounts);
    }

    // Pin conversation for a user
    @PostMapping("/{conversationId}/pin-conversation")
    public ResponseEntity<Map<String, Object>> pinConversation(
            @PathVariable String conversationId
    ) {
        boolean success = conversationService.pinConversation(conversationId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (success) {
            response.put("message", "Conversation pinned successfully");
        } else {
            response.put("message", "Failed to pin conversation");
        }
        return success ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    // Unpin conversation for a user
    @PostMapping("/{conversationId}/unpin-conversation")
    public ResponseEntity<Map<String, Object>> unpinConversation(
            @PathVariable String conversationId
    ) {
        boolean success = conversationService.unpinConversation(conversationId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (success) {
            response.put("message", "Conversation unpinned successfully");
        } else {
            response.put("message", "Failed to unpin conversation");
        }
        return success ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    // Mark conversation as unread for a user
    @PostMapping("/{conversationId}/mark-unread")
    public ResponseEntity<Map<String, Object>> markConversationAsUnread(
            @PathVariable String conversationId
    ) {
        boolean success = conversationService.markConversationAsUnread(conversationId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (success) {
            response.put("message", "Conversation marked as unread successfully");
        } else {
            response.put("message", "Failed to mark conversation as unread");
        }
        return success ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    // Toggle notification settings for a user
    @PostMapping("/{conversationId}/toggle-notifications")
    public ResponseEntity<Map<String, Object>> toggleNotificationSettings(
            @PathVariable String conversationId
    ) {
        boolean success = conversationService.toggleNotificationSettings(conversationId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (success) {
            response.put("message", "Notification settings updated successfully");
        } else {
            response.put("message", "Failed to update notification settings");
        }
        return success ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }
    
    // Delete group conversation (only by creator)
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Map<String, Object>> deleteGroupConversation(
            @PathVariable String conversationId,
            @RequestParam(required = false) String actorUserId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Resolve actor user id from request param or security context
            String finalActorUserId;
            if (actorUserId == null || actorUserId.isBlank()) {
                try {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.getPrincipal() instanceof com.iems.chatservice.security.JwtUserDetails jwt) {
                        finalActorUserId = jwt.getUserId().toString();
                    } else {
                        finalActorUserId = null;
                    }
                } catch (Exception ignore) { 
                    finalActorUserId = null;
                }
            } else {
                finalActorUserId = actorUserId;
            }
            
            if (finalActorUserId == null || finalActorUserId.isBlank()) {
                response.put("success", false);
                response.put("message", "User not authenticated");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Check if conversation exists and is a GROUP
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) {
                response.put("success", false);
                response.put("message", "Conversation not found");
                return ResponseEntity.notFound().build();
            }
            
            if (!"GROUP".equalsIgnoreCase(conversation.getType())) {
                response.put("success", false);
                response.put("message", "Only group conversations can be deleted");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Check if user is the creator
            if (!finalActorUserId.equals(conversation.getCreatedBy())) {
                response.put("success", false);
                response.put("message", "Only the group creator can delete the conversation");
                return ResponseEntity.status(403).build();
            }
            
            // Delete all messages in the conversation
            Query messageQuery = new Query(Criteria.where("conversationId").is(conversationId));
            mongoTemplate.remove(messageQuery, com.iems.chatservice.entity.Message.class);
            
            // Delete the conversation
            conversationRepository.deleteById(conversationId);
            
            // Broadcast deletion event to all members
            try {
                List<String> members = conversation.getMembers();
                if (members != null) {
                    var payload = java.util.Map.of(
                            "event", "conversation_deleted",
                            "conversationId", conversationId,
                            "deletedBy", finalActorUserId,
                            "type", "GROUP"
                    );
                    for (String memberId : members) {
                        messagingTemplate.convertAndSend("/topic/user-updates/" + memberId, payload);
                    }
                }
            } catch (Exception ignore) { }
            
            response.put("success", true);
            response.put("message", "Group conversation deleted successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to delete conversation: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    // Helper method to resolve user name
    private String resolveUserName(String userId) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(userId);
            var resp = userServiceFeignClient.getUserById(uuid);
            var body = resp.getBody();
            if (body != null && body.containsKey("data")) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> data = (java.util.Map<String, Object>) body.get("data");
                String firstName = data.getOrDefault("firstName", "").toString();
                String lastName = data.getOrDefault("lastName", "").toString();
                String email = data.getOrDefault("email", "").toString();
                String full = (firstName + " " + lastName).trim();
                return full.isBlank() ? (email.isBlank() ? userId : email) : full;
            }
        } catch (Exception e) {
            // Log the error for debugging
            System.err.println("Error resolving user name for " + userId + ": " + e.getMessage());
        }
        // Return a more user-friendly fallback instead of raw userId
        return "Người dùng";
    }
}



