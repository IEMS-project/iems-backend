package com.iems.aiservice.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "document_vector_chunks")
public class DocumentVectorChunk {
    @Id
    private String id;
    private String projectId;
    private String documentId;
    private String fileName;
    private String contentHash;
    private int chunkIndex;
    private String chunkText;
    private List<Double> embedding;
    private Instant updatedAt;
}
