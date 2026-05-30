package com.iems.aiservice.controller;

import com.iems.aiservice.config.AiProperties;
import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.dto.AgentChatResponse;
import com.iems.aiservice.dto.ChatRequest;
import com.iems.aiservice.dto.ChatResponse;
import com.iems.aiservice.dto.DocumentContextResult;
import com.iems.aiservice.dto.HealthResponse;
import com.iems.aiservice.entity.ChatMessage;
import com.iems.aiservice.entity.Conversation;
import com.iems.aiservice.model.agent.AgentDecision;
import com.iems.aiservice.model.agent.AgentIntent;
import com.iems.aiservice.service.ChatHistoryService;
import com.iems.aiservice.service.DocumentContextService;
import com.iems.aiservice.service.JwtService;
import com.iems.aiservice.service.OpenRouterChatService;
import com.iems.aiservice.service.agent.AgentIntentRouterService;
import com.iems.aiservice.service.agent.AgentOrchestratorService;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AiChatController {

    private final OpenRouterChatService openRouterChatService;
    private final AiProperties aiProperties;
    private final JwtService jwtService;
    private final ChatHistoryService chatHistoryService;
    private final DocumentContextService documentContextService;
    private final AgentOrchestratorService agentOrchestratorService;
    private final AgentIntentRouterService agentIntentRouterService;
    private final MongoTemplate mongoTemplate;

    public AiChatController(OpenRouterChatService openRouterChatService, AiProperties aiProperties, JwtService jwtService,
            ChatHistoryService chatHistoryService, DocumentContextService documentContextService,
            AgentOrchestratorService agentOrchestratorService,
            AgentIntentRouterService agentIntentRouterService,
            MongoTemplate mongoTemplate) {
        this.openRouterChatService = openRouterChatService;
        this.aiProperties = aiProperties;
        this.jwtService = jwtService;
        this.chatHistoryService = chatHistoryService;
        this.documentContextService = documentContextService;
        this.agentOrchestratorService = agentOrchestratorService;
        this.agentIntentRouterService = agentIntentRouterService;
        this.mongoTemplate = mongoTemplate;
    }

    @PostMapping("/chat/agent")
    public ResponseEntity<AgentChatResponse> chatAgent(
            @Valid @RequestBody AgentChatRequest request,
            @RequestHeader("Authorization") String authorization) {
        String userId = extractUserIdFromAuthorization(authorization);

        String conversationId = chatHistoryService.ensureConversation(
                request.conversationId(), userId, request.question(), request.projectId());
        chatHistoryService.saveMessage(conversationId, "user", request.question());

        DocumentContextResult documentContextResult = documentContextService.buildDocumentContextResult(
                request.projectId(), request.selectedDocumentIds(), request.question());
        String documentContext = documentContextResult.context();
        String conversationContext = chatHistoryService.buildRecentConversationContext(conversationId);

        AgentChatResponse response = agentOrchestratorService.handle(
                userId,
                conversationId,
                request,
                authorization,
                documentContext,
                conversationContext,
                aiProperties.getModel());

        chatHistoryService.saveMessage(conversationId, "assistant", response.answer());
        chatHistoryService.updateTimestamp(conversationId);

        return ResponseEntity.ok(response);
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

        DocumentContextResult documentContextResult = documentContextService.buildDocumentContextResult(
                request.projectId(), request.selectedDocumentIds(), request.question());
        String documentContext = documentContextResult.context();
        String conversationContext = chatHistoryService.buildRecentConversationContext(conversationId);
        log.info("Chat request projectId={} selectedCount={} contextChars={} conversationId={}",
                request.projectId(),
                request.selectedDocumentIds() == null ? 0 : request.selectedDocumentIds().size(),
                documentContext.length(),
                conversationId);
        log.info("Chat memory conversationId={} memoryChars={}", conversationId, conversationContext.length());

        AgentChatResponse agentResponse;
        String answer;
        if (!documentContextResult.sources().isEmpty()) {
            answer = openRouterChatService.ask(
                    request.question(),
                    request.selectedDocumentIds(),
                    documentContext,
                    conversationContext);
            agentResponse = new AgentChatResponse(
                    answer,
                    aiProperties.getModel(),
                    conversationId,
                    Instant.now(),
                    "DOCUMENT_CHAT",
                    0.95,
                    List.of(),
                    documentContextResult.sources().stream()
                            .map(source -> source.fileName() + " #" + source.chunkIndex())
                            .toList());
        } else {
            AgentChatRequest agentRequest = new AgentChatRequest(
                    request.question(),
                    conversationId,
                    request.projectId(),
                    request.selectedDocumentIds());

            agentResponse = agentOrchestratorService.handle(
                    userId,
                    conversationId,
                    agentRequest,
                    authorization,
                    documentContext,
                    conversationContext,
                    aiProperties.getModel());
            answer = agentResponse.answer();
        }
        chatHistoryService.saveMessage(conversationId, "assistant", answer);
        chatHistoryService.updateTimestamp(conversationId);

        ChatResponse response = new ChatResponse(
                answer,
                aiProperties.getModel(),
                conversationId,
                Instant.now(),
                agentResponse.intent(),
                agentResponse.confidence(),
                documentContextResult.sources());

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

        DocumentContextResult documentContextResult = documentContextService.buildDocumentContextResult(
                request.projectId(), request.selectedDocumentIds(), request.question());
        String documentContext = documentContextResult.context();
        String conversationContext = chatHistoryService.buildRecentConversationContext(conversationId);
        log.info("Stream chat request projectId={} selectedCount={} contextChars={} conversationId={}",
                request.projectId(),
                request.selectedDocumentIds() == null ? 0 : request.selectedDocumentIds().size(),
                documentContext.length(),
                conversationId);
        log.info("Stream chat memory conversationId={} memoryChars={}", conversationId, conversationContext.length());

        SseEmitter emitter = new SseEmitter(0L);
        StringBuilder fullAnswer = new StringBuilder();

        CompletableFuture.runAsync(() -> {
            try {
                if (!documentContextResult.sources().isEmpty()) {
                    openRouterChatService.streamAsk(request.question(), request.selectedDocumentIds(), documentContext,
                            conversationContext,
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

                    chatHistoryService.saveMessage(conversationId, "assistant", fullAnswer.toString());
                    chatHistoryService.updateTimestamp(conversationId);

                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "end",
                            "conversationId", conversationId,
                            "intent", "DOCUMENT_CHAT",
                            "sources", documentContextResult.sources())));
                    emitter.complete();
                    return;
                }

                AgentDecision decision = agentIntentRouterService.route(request.question());
                if (decision.intent() == AgentIntent.ISSUE_QUERY
                        || decision.intent() == AgentIntent.ISSUE_ACTION
                        || decision.intent() == AgentIntent.ISSUE_ANALYSIS) {
                    AgentChatRequest agentRequest = new AgentChatRequest(
                            request.question(),
                            conversationId,
                            request.projectId(),
                            request.selectedDocumentIds());
                    AgentChatResponse agentResponse = agentOrchestratorService.handle(
                            userId,
                            conversationId,
                            agentRequest,
                            authorization,
                            documentContext,
                            conversationContext,
                            aiProperties.getModel());

                    String answer = agentResponse.answer() == null ? "" : agentResponse.answer();
                    fullAnswer.append(answer);
                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "chunk",
                            "content", answer)));
                    chatHistoryService.saveMessage(conversationId, "assistant", fullAnswer.toString());
                    chatHistoryService.updateTimestamp(conversationId);
                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "end",
                            "conversationId", conversationId,
                            "sources", documentContextResult.sources())));
                    emitter.complete();
                    return;
                }

                openRouterChatService.streamAsk(request.question(), request.selectedDocumentIds(), documentContext,
                        conversationContext,
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

                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "end",
                        "conversationId", conversationId,
                        "sources", documentContextResult.sources())));
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

    @GetMapping("/storage")
    public ResponseEntity<?> storage(@RequestHeader("Authorization") String authorization) {
        extractUserIdFromAuthorization(authorization);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "database", mongoTemplate.getDb().getName(),
                "collections", Map.of(
                        "conversations", mongoTemplate.getCollection("conversations").countDocuments(),
                        "chat_messages", mongoTemplate.getCollection("chat_messages").countDocuments(),
                        "document_vector_chunks", mongoTemplate.getCollection("document_vector_chunks").countDocuments())));
    }

    @PostMapping(path = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocumentForChat(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("projectId") String projectId,
            @RequestParam("file") MultipartFile file) throws IOException {
        extractUserIdFromAuthorization(authorization);
        if (projectId == null || projectId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "projectId is required to attach a document to chat");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file is empty");
        }

        String docId = "chat-upload-" + UUID.randomUUID();
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? docId
                : file.getOriginalFilename();
        String fileType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();

        try {
            byte[] fileBytes = file.getBytes();
            if (isImage(fileName, fileType)) {
                String description = openRouterChatService.describeImage(fileName, fileType, fileBytes);
                documentContextService.indexTextDocument(
                        projectId,
                        docId,
                        fileName,
                        "Image file: " + fileName + "\n\nExtracted visual content:\n" + description);
            } else {
                documentContextService.indexUploadedDocument(
                        projectId,
                        docId,
                        fileName,
                        fileType,
                        fileBytes);
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Unable to read uploaded file with AI: " + ex.getMessage(),
                    ex);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", docId,
                "fileName", fileName,
                "fileType", fileType,
                "projectId", projectId,
                "allowEmbedded", true));
    }

    private boolean isImage(String fileName, String fileType) {
        String lowerFileName = fileName == null ? "" : fileName.toLowerCase();
        String lowerFileType = fileType == null ? "" : fileType.toLowerCase();
        return lowerFileType.startsWith("image/")
                || lowerFileName.endsWith(".png")
                || lowerFileName.endsWith(".jpg")
                || lowerFileName.endsWith(".jpeg")
                || lowerFileName.endsWith(".webp");
    }

    @GetMapping("/options")
    public ResponseEntity<?> getQuickOptions(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "projectId", required = false) String projectId) {
        extractUserIdFromAuthorization(authorization);

        List<Map<String, String>> options = List.of(
                Map.of(
                        "id", "daily_plan",
                        "label", "Lập kế hoạch hôm nay",
                        "prompt", "Đọc các issue/task trong dự án này và lập kế hoạch làm việc hôm nay cho tôi. Ưu tiên việc quan trọng, việc gần deadline, blocker, và đưa ra thứ tự nên làm kèm lý do."),
                Map.of(
                        "id", "project_risk_review",
                        "label", "Phân tích rủi ro",
                        "prompt", "Phân tích tình hình công việc hiện tại trong dự án: task nào đang rủi ro, task nào có khả năng trễ, blocker nằm ở đâu, và đề xuất hành động tiếp theo."),
                Map.of(
                        "id", "progress_summary",
                        "label", "Tóm tắt tiến độ",
                        "prompt", "Tóm tắt tiến độ dự án hiện tại theo nhóm: việc đã xong, việc đang làm, việc bị kẹt, việc cần ưu tiên. Trả lời ngắn gọn nhưng đủ để báo cáo standup."),
                Map.of(
                        "id", "next_actions",
                        "label", "Đề xuất bước tiếp theo",
                        "prompt", "Dựa trên dữ liệu issue/task hiện tại, hãy đề xuất 5 hành động tiếp theo tôi nên làm để đẩy dự án tiến lên. Nếu có issue cụ thể, nêu issue key và lý do."));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "projectId", projectId == null ? "" : projectId,
                "options", options));
    }
}
