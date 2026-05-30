package com.iems.aiservice.dto;

import java.time.Instant;
import java.util.List;

public record ChatResponse(
                String answer,
                String model,
                String conversationId,
                Instant timestamp,
                String intent,
                double confidence,
                List<RetrievedDocumentSource> sources) {
}
