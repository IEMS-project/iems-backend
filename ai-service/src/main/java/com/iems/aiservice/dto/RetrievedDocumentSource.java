package com.iems.aiservice.dto;

public record RetrievedDocumentSource(
                String documentId,
                String fileName,
                int chunkIndex,
                double score) {
}
