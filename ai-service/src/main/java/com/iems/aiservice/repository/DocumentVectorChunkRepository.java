package com.iems.aiservice.repository;

import com.iems.aiservice.entity.DocumentVectorChunk;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentVectorChunkRepository extends MongoRepository<DocumentVectorChunk, String> {
    List<DocumentVectorChunk> findByProjectIdAndDocumentId(String projectId, String documentId);
    List<DocumentVectorChunk> findByProjectIdAndDocumentIdIn(String projectId, List<String> documentIds);
    void deleteByProjectIdAndDocumentId(String projectId, String documentId);
}
