package com.iems.aiservice.controller;

import com.iems.aiservice.config.AiProperties;
import com.iems.aiservice.dto.ChatRequest;
import com.iems.aiservice.dto.ChatResponse;
import com.iems.aiservice.dto.HealthResponse;
import com.iems.aiservice.entity.ChatMessage;
import com.iems.aiservice.entity.Conversation;
import com.iems.aiservice.service.ChatHistoryService;
import com.iems.aiservice.service.DocumentContextService;
import com.iems.aiservice.service.JwtService;
import com.iems.aiservice.service.OllamaChatService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AiChatController {

    private final OllamaChatService ollamaChatService;
    private final AiProperties aiProperties;
    private final JwtService jwtService;
    private final ChatHistoryService chatHistoryService;
    private final DocumentContextService documentContextService;

    public AiChatController(OllamaChatService ollamaChatService, AiProperties aiProperties, JwtService jwtService,
            ChatHistoryService chatHistoryService, DocumentContextService documentContextService) {
        this.ollamaChatService = ollamaChatService;
        this.aiProperties = aiProperties;
        this.jwtService = jwtService;
        this.chatHistoryService = chatHistoryService;
        this.documentContextService = documentContextService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader("Authorization") String authorization) {
        String userId = extractUserIdFromAuthorization(authorization);

        // Ensure conversation exists or create new one
        String conversationId = chatHistoryService.ensureConversation(
                request.conversationId(), userId, request.question(), request.projectId());
        chatHistoryService.saveMessage(conversationId, "user", request.question());

        String documentContext = documentContextService.buildDocumentContext(
                request.projectId(), request.selectedDocumentIds(), request.question());
        log.info("Chat request projectId={} selectedCount={} contextChars={} conversationId={}",
                request.projectId(),
                request.selectedDocumentIds() == null ? 0 : request.selectedDocumentIds().size(),
                documentContext.length(),
                conversationId);

        String answer = ollamaChatService.ask(request.question(), request.selectedDocumentIds(), documentContext);
        chatHistoryService.saveMessage(conversationId, "assistant", answer);
        chatHistoryService.updateTimestamp(conversationId);

        ChatResponse response = new ChatResponse(
                answer,
                aiProperties.getModel(),
                conversationId,
                Instant.now());

        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader("Authorization") String authorization) {
        String userId = extractUserIdFromAuthorization(authorization);

        // Ensure conversation exists or create new one
        String conversationId = chatHistoryService.ensureConversation(
                request.conversationId(), userId, request.question(), request.projectId());
        chatHistoryService.saveMessage(conversationId, "user", request.question());

        String documentContext = documentContextService.buildDocumentContext(
                request.projectId(), request.selectedDocumentIds(), request.question());
        log.info("Stream chat request projectId={} selectedCount={} contextChars={} conversationId={}",
                request.projectId(),
                request.selectedDocumentIds() == null ? 0 : request.selectedDocumentIds().size(),
                documentContext.length(),
                conversationId);

        SseEmitter emitter = new SseEmitter(0L);
        StringBuilder fullAnswer = new StringBuilder();

        CompletableFuture.runAsync(() -> {
            try {
                ollamaChatService.streamAsk(request.question(), request.selectedDocumentIds(), documentContext,
                        chunk -> {
                            try {
                                fullAnswer.append(chunk);
                                emitter.send(SseEmitter.event().data(Map.of(
                                        "type", "chunk",
                                        "content", chunk)));
                            } catch (Exception sendException) {
                                throw new RuntimeException(sendException);
                            }
                        });

                // End of stream, save assistant message
                chatHistoryService.saveMessage(conversationId, "assistant", fullAnswer.toString());
                chatHistoryService.updateTimestamp(conversationId);

                emitter.send(SseEmitter.event().data(Map.of("type", "end")));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "error",
                            "error", e.getMessage() == null ? "Streaming failed" : e.getMessage())));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String extractUserIdFromAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authorization.substring(7).trim();
        if (token.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Empty bearer token");
        }

        try {
            String userId = jwtService.extractUserId(token);
            if (userId == null || userId.isBlank()) {
                throw new ResponseStatusException(UNAUTHORIZED, "JWT does not contain user identifier");
            }
            return userId;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid JWT token");
        }
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(@RequestHeader("Authorization") String authorization) {
        String userId = extractUserIdFromAuthorization(authorization);
        List<Conversation> conversations = chatHistoryService.getUserConversations(userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "conversations", conversations));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<?> getMessages(@PathVariable String id,
            @RequestHeader("Authorization") String authorization) {
        extractUserIdFromAuthorization(authorization); // just validate token
        List<ChatMessage> messages = chatHistoryService.getConversationMessages(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "messages", messages));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<?> deleteConversation(@PathVariable String id,
            @RequestHeader("Authorization") String authorization) {
        extractUserIdFromAuthorization(authorization);
        chatHistoryService.deleteConversation(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/memory/clear")
    public ResponseEntity<?> clearMemory(@RequestHeader("Authorization") String authorization) {
        String userId = extractUserIdFromAuthorization(authorization);
        chatHistoryService.clearAllMemory(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/conversations/{id}/rename")
    public ResponseEntity<?> renameConversation(
            @PathVariable String id,
            @RequestParam("new_name") String newName,
            @RequestHeader("Authorization") String authorization) {
        extractUserIdFromAuthorization(authorization);
        Conversation updated = chatHistoryService.renameConversation(id, newName);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "conversation", updated != null ? updated : Map.of()));
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("ai-service", "UP", aiProperties.getModel()));
    }
}
