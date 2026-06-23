package com.iems.notificationservice.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry();

    @Test
    void createEmitterShouldRegisterUser() {
        UUID userId = UUID.randomUUID();

        registry.createEmitter(userId);

        assertTrue(registry.isOnline(userId));
        assertEquals(1, registry.getOnlineCount());
    }

    @Test
    void pushShouldReturnFalseForMissingEmitter() {
        assertFalse(registry.push(UUID.randomUUID(), "payload"));
    }
}