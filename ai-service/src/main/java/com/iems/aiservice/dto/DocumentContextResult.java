package com.iems.aiservice.dto;

import java.util.List;

public record DocumentContextResult(
                String context,
                List<RetrievedDocumentSource> sources) {

    public static DocumentContextResult empty() {
        return new DocumentContextResult("", List.of());
    }
}
