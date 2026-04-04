package com.iems.aiservice.dto;

import java.time.Instant;

public record ChatResponse(
                String answer,
                String model,
                String conversationId,
                Instant timestamp) {
}
