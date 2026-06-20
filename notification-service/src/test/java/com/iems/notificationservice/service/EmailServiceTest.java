package com.iems.notificationservice.service;

import com.iems.notificationservice.client.UserClient;
import com.iems.notificationservice.dto.ApiResponse;
import com.iems.notificationservice.dto.CreateNotificationRequest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private UserClient userClient;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, templateEngine, userClient);
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://frontend.example.com");
        ReflectionTestUtils.setField(emailService, "emailEnabled", true);
    }

    @Test
    void sendNotificationEmailShouldRenderAndSendWhenEnabled() {
        CreateNotificationRequest request = baseRequest();
        MimeMessage message = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(message);
        when(templateEngine.process(anyString(), any(org.thymeleaf.context.Context.class))).thenReturn("<html>ok</html>");
        when(userClient.getNotificationPreferences(request.getRecipientId()))
                .thenReturn(new ApiResponse<>("success", "ok", Map.of("emailAssigned", true)));

        emailService.sendNotificationEmail(request, request.getRecipientEmail(), request.getRecipientName());

        verify(mailSender).send(message);
        verify(templateEngine).process(anyString(), any(org.thymeleaf.context.Context.class));
    }

    @Test
    void sendNotificationEmailShouldSkipWhenDisabledByPreference() {
        CreateNotificationRequest request = baseRequest();
        when(userClient.getNotificationPreferences(request.getRecipientId()))
                .thenReturn(new ApiResponse<>("success", "ok", Map.of("emailAssigned", false)));

        emailService.sendNotificationEmail(request, request.getRecipientEmail(), request.getRecipientName());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendNotificationEmailShouldSkipWhenNoRecipientEmail() {
        CreateNotificationRequest request = baseRequest();

        emailService.sendNotificationEmail(request, "", request.getRecipientName());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private CreateNotificationRequest baseRequest() {
        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setRecipientId(UUID.randomUUID());
        request.setRecipientEmail("user@example.com");
        request.setRecipientName("User");
        request.setType("ISSUE_ASSIGNED");
        request.setTitle("Task 1");
        request.setBody("Body");
        request.setProjectName("Project");
        request.setLinkPath("/projects/1");
        request.setActorName("Actor");
        return request;
    }
}