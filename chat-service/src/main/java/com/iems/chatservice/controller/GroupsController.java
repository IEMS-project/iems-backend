package com.iems.chatservice.controller;

import com.iems.chatservice.entity.Conversation;
import com.iems.chatservice.entity.Message;
import com.iems.chatservice.service.MessageBroadcastService;
import com.iems.chatservice.client.UserServiceFeignClient;
import com.iems.chatservice.repository.ConversationRepository;
import com.iems.chatservice.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupsController {

    private final ConversationRepository conversationRepository;
    private final MessageBroadcastService messageBroadcastService;
    private final UserServiceFeignClient userServiceFeignClient;

    @PostMapping
    public ResponseEntity<Conversation> createGroup(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        @SuppressWarnings("unchecked")
        List<String> members = (List<String>) body.get("members");

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String currentUserId = getCurrentUserId();

        Conversation group = new Conversation();
        group.setType("GROUP");
        group.setName(name);
        group.setAvatarUrl(null); // image default null
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        group.setCreatedBy(currentUserId);

        List<String> finalMembers = new ArrayList<>();
        if (members != null) {
            for (String m : members) {
                if (m != null && !m.isBlank() && !finalMembers.contains(m)) {
                    finalMembers.add(m);
                }
            }
        }
        if (currentUserId != null && !currentUserId.isBlank() && !finalMembers.contains(currentUserId)) {
            finalMembers.add(currentUserId);
        }
        group.setMembers(finalMembers);

        Conversation saved = conversationRepository.save(group);
        try {
            Message log = new Message();
            log.setConversationId(saved.getId());
            log.setSenderId("SYSTEM");
            log.setType("SYSTEM_LOG");
            log.setContent("Nhóm đã được tạo");
            log.setSentAt(java.time.LocalDateTime.now());
            messageBroadcastService.saveAndBroadcast(log);
            // Broadcast meta so sidebar list can update immediately
            messageBroadcastService.broadcastConversationMetaUpdate(saved, java.util.Map.of(
                    "name", saved.getName(),
                    "avatarUrl", saved.getAvatarUrl(),
                    "updatedAt", saved.getUpdatedAt()
            ));
        } catch (Exception ignored) { }
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<Conversation> getGroup(@PathVariable String groupId) {
        return conversationRepository.findById(groupId)
                .filter(conv -> "GROUP".equalsIgnoreCase(conv.getType()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{groupId}/name")
    public ResponseEntity<Conversation> updateGroupName(@PathVariable String groupId,
                                                        @RequestBody Map<String, String> body) {
        String newName = body.get("name");
        if (newName == null || newName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Conversation> opt = conversationRepository.findById(groupId);
        if (opt.isEmpty() || !"GROUP".equalsIgnoreCase(opt.get().getType())) {
            return ResponseEntity.notFound().build();
        }

        Conversation group = opt.get();
        if (!canManageGroup(group)) {
            return ResponseEntity.status(403).build();
        }
        String oldName = group.getName();
        group.setName(newName);
        group.setUpdatedAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(group);
        try {
            String actor = resolveUserNameSafe(getCurrentUserId());
            Message log = new Message();
            log.setConversationId(saved.getId());
            log.setSenderId("SYSTEM");
            log.setType("SYSTEM_LOG");
            log.setContent(String.format("%s đã đổi tên nhóm%s", actor,
                    (oldName != null && !oldName.isBlank()) ? String.format(" từ \"%s\" thành \"%s\"", oldName, newName) : String.format(" thành \"%s\"", newName)));
            log.setSentAt(java.time.LocalDateTime.now());
            messageBroadcastService.saveAndBroadcast(log);
            messageBroadcastService.broadcastConversationMetaUpdate(saved, java.util.Map.of(
                    "name", saved.getName(),
                    "updatedAt", saved.getUpdatedAt()
            ));
        } catch (Exception ignored) { }
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{groupId}/avatar")
    public ResponseEntity<Conversation> updateGroupAvatar(@PathVariable String groupId,
                                                          @RequestBody Map<String, String> body) {
        String imageUrl = body.get("imageUrl");
        if (imageUrl == null || imageUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Conversation> opt = conversationRepository.findById(groupId);
        if (opt.isEmpty() || !"GROUP".equalsIgnoreCase(opt.get().getType())) {
            return ResponseEntity.notFound().build();
        }
        Conversation group = opt.get();
        if (!canManageGroup(group)) {
            return ResponseEntity.status(403).build();
        }
        group.setAvatarUrl(imageUrl);
        group.setUpdatedAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(group);
        try {
            String actor = resolveUserNameSafe(getCurrentUserId());
            Message log = new Message();
            log.setConversationId(saved.getId());
            log.setSenderId("SYSTEM");
            log.setType("SYSTEM_LOG");
            log.setContent(String.format("%s đã cập nhật ảnh nhóm", actor));
            log.setSentAt(java.time.LocalDateTime.now());
            messageBroadcastService.saveAndBroadcast(log);
            messageBroadcastService.broadcastConversationMetaUpdate(saved, java.util.Map.of(
                    "avatarUrl", saved.getAvatarUrl(),
                    "updatedAt", saved.getUpdatedAt()
            ));
        } catch (Exception ignored) { }
        return ResponseEntity.ok(saved);
    }

    private String getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof JwtUserDetails jwt) {
                return jwt.getUserId().toString();
            }
        } catch (Exception ignored) { }
        return null;
    }

    private boolean hasAdminRole() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                for (GrantedAuthority ga : auth.getAuthorities()) {
                    if (ga.getAuthority() != null && ga.getAuthority().startsWith("ROLE_")
                            && ga.getAuthority().equals("ROLE_ADMIN")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    private boolean canManageGroup(Conversation group) {
        String uid = getCurrentUserId();
        if (uid == null) return false;
        if (hasAdminRole()) return true;
        return uid.equals(group.getCreatedBy());
    }

    private String resolveUserNameSafe(String userIdStr) {
        try {
            if (userIdStr == null) return "Ai đó";
            java.util.UUID uid = java.util.UUID.fromString(userIdStr);
            var resp = userServiceFeignClient.getUserById(uid);
            var body = resp.getBody();
            if (body != null) {
                Object data = body.get("data");
                if (data instanceof java.util.Map<?, ?> map) {
                    String first = asString(map.get("firstName"));
                    String last = asString(map.get("lastName"));
                    String full = (first + " " + last).trim();
                    if (!full.isBlank()) return full;
                    String email = asString(map.get("email"));
                    if (email != null && !email.isBlank()) return email;
                }
            }
        } catch (Exception ignored) { }
        return "Ai đó";
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}



