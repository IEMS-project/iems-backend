package com.iems.aiservice.service;

import com.iems.aiservice.entity.ChatMessage;
import com.iems.aiservice.entity.Conversation;
import com.iems.aiservice.repository.ChatMessageRepository;
import com.iems.aiservice.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ChatHistoryService chatHistoryService;

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversation = new Conversation();
        conversation.setId("conv-1");
        conversation.setUserId("user-1");
        conversation.setProjectId("project-1");
        conversation.setName("Old");
        conversation.setCreatedAt(Instant.now());
        conversation.setUpdatedAt(Instant.now());
    }

    @Test
    void ensureConversationShouldReuseExistingConversation() {
        when(conversationRepository.existsById("conv-1")).thenReturn(true);
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conversation));

        String id = chatHistoryService.ensureConversation("conv-1", "user-1", "first message", "project-1");

        assertEquals("conv-1", id);
        verify(conversationRepository).save(conversation);
    }

    @Test
    void ensureConversationShouldCreateNewConversationWithDefaultName() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String id = chatHistoryService.ensureConversation(null, "user-1", "   ", "project-1");

        assertNotNull(id);
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertEquals("New Conversation", captor.getValue().getName());
    }

    @Test
    void ensureConversationShouldTrimAndShortenName() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String longMessage = "a".repeat(61);

        chatHistoryService.ensureConversation("", "user-1", longMessage, "project-1");

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertTrue(captor.getValue().getName().endsWith("..."));
    }

    @Test
    void buildRecentConversationContextShouldHandleEmptyAndLimitHistory() {
        assertEquals("", chatHistoryService.buildRecentConversationContext(null));
        assertEquals("", chatHistoryService.buildRecentConversationContext("   "));

        List<ChatMessage> messages = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(i -> {
                    ChatMessage message = new ChatMessage();
                    message.setRole(i % 2 == 0 ? "assistant" : "user");
                    message.setContent("msg" + i);
                    return message;
                })
                .toList();
        when(chatMessageRepository.findByConversationIdOrderByTimestampAsc("conv-1")).thenReturn(messages);

        String context = chatHistoryService.buildRecentConversationContext("conv-1");

        String expected = java.util.stream.IntStream.rangeClosed(2, 11)
            .mapToObj(i -> (i % 2 == 0 ? "Assistant" : "User") + ": msg" + i)
            .collect(java.util.stream.Collectors.joining("\n"));

        assertEquals(expected, context);
    }

    @Test
    void deleteConversationShouldDeleteBothSides() {
        chatHistoryService.deleteConversation("conv-1");

        verify(conversationRepository).deleteById("conv-1");
        verify(chatMessageRepository).deleteByConversationId("conv-1");
    }

    @Test
    void renameConversationShouldReturnNullWhenMissing() {
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.empty());

        assertEquals(null, chatHistoryService.renameConversation("conv-1", "New Name"));
        verify(conversationRepository, never()).save(any());
    }
}