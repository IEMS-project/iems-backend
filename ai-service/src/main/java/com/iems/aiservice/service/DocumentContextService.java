package com.iems.aiservice.service;

import com.iems.aiservice.config.AiProperties;
import com.iems.aiservice.dto.DocumentContextResult;
import com.iems.aiservice.dto.RetrievedDocumentSource;
import com.iems.aiservice.entity.DocumentVectorChunk;
import com.iems.aiservice.repository.DocumentVectorChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentContextService {

    private static final int MAX_SELECTED_DOCS = 5;
    private static final int MAX_CONTEXT_CHARS = 7000;

    private final DocumentVectorChunkRepository documentVectorChunkRepository;
    private final OpenRouterEmbeddingService openRouterEmbeddingService;
    private final AiProperties aiProperties;

    /**
     * Indexes document context data for search or AI processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param docId the doc id parameter
     * @param fileName the file name parameter
     * @param fileType the file type parameter
     * @param downloadUrl the download url parameter
     */
    public void indexDocument(String projectId,
            String docId,
            String fileName,
            String fileType,
            String downloadUrl) {
        String resolvedFileName = (fileName == null || fileName.isBlank()) ? docId : fileName;
        log.info("Index start projectId={} docId={} fileName={} fileType={} hasDownloadUrl={}",
                projectId,
                docId,
                resolvedFileName,
                fileType,
                downloadUrl != null && !downloadUrl.isBlank());
        if (!isSupportedForEmbedding(resolvedFileName, fileType)) {
            log.info("Skipping unsupported document docId={} fileName={} fileType={}", docId, resolvedFileName,
                    fileType);
            return;
        }

        RestClient restClient = RestClient.builder().build();
        byte[] contentBytes = restClient.get()
                .uri(URI.create(downloadUrl))
                .retrieve()
                .body(byte[].class);

        int contentLength = contentBytes != null ? contentBytes.length : 0;
        log.info("Index download completed projectId={} docId={} rawBytes={}", projectId, docId, contentLength);

        String content = extractTextContent(contentBytes, resolvedFileName, fileType);
        int extractedLength = content != null ? content.length() : 0;
        log.info("Index extraction completed projectId={} docId={} extractedChars={}", projectId, docId,
                extractedLength);

        if (content == null || content.isBlank()) {
            log.info("Skipping index because extracted text is empty projectId={} docId={} fileName={}",
                    projectId,
                    docId,
                    resolvedFileName);
            return;
        }

        upsertDocumentEmbeddings(projectId, docId, resolvedFileName, content);
    }

    /**
     * Indexes document context data for search or AI processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param docId the doc id parameter
     * @param fileName the file name parameter
     * @param fileType the file type parameter
     * @param contentBytes the content bytes parameter
     * @throws IllegalArgumentException if the request contains invalid arguments
     */
    public void indexUploadedDocument(String projectId,
            String docId,
            String fileName,
            String fileType,
            byte[] contentBytes) {
        String resolvedFileName = (fileName == null || fileName.isBlank()) ? docId : fileName;
        log.info("Upload index start projectId={} docId={} fileName={} fileType={} rawBytes={}",
                projectId,
                docId,
                resolvedFileName,
                fileType,
                contentBytes == null ? 0 : contentBytes.length);
        if (!isSupportedForEmbedding(resolvedFileName, fileType)) {
            throw new IllegalArgumentException("Unsupported document type for AI reading: " + resolvedFileName);
        }

        String content = extractTextContent(contentBytes, resolvedFileName, fileType);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Unable to extract text from uploaded document: " + resolvedFileName);
        }

        upsertDocumentEmbeddings(projectId, docId, resolvedFileName, content);
    }

    /**
     * Indexes document context data for search or AI processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param docId the doc id parameter
     * @param fileName the file name parameter
     * @param content the content parameter
     * @throws IllegalArgumentException if the request contains invalid arguments
     */
    public void indexTextDocument(String projectId,
            String docId,
            String fileName,
            String content) {
        String resolvedFileName = (fileName == null || fileName.isBlank()) ? docId : fileName;
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Document content is empty: " + resolvedFileName);
        }
        upsertDocumentEmbeddings(projectId, docId, resolvedFileName, content);
    }

    /**
     * Performs deindex document for document context processing.
     *
     * @param projectId the project id parameter
     * @param docId the doc id parameter
     */
    public void deindexDocument(String projectId, String docId) {
        log.info("Deindex start projectId={} docId={}", projectId, docId);
        documentVectorChunkRepository.deleteByProjectIdAndDocumentId(projectId, docId);
        log.info("Deindex completed projectId={} docId={}", projectId, docId);
    }

    /**
     * Builds document context data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param selectedDocumentIds the selected document ids parameter
     * @param question the question parameter
     * @return the build document context result
     */
    public String buildDocumentContext(String projectId,
            List<String> selectedDocumentIds,
            String question) {
        return buildDocumentContextResult(projectId, selectedDocumentIds, question).context();
    }

    /**
     * Builds document context data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param selectedDocumentIds the selected document ids parameter
     * @param question the question parameter
     * @return the build document context result result
     */
    public DocumentContextResult buildDocumentContextResult(String projectId,
            List<String> selectedDocumentIds,
            String question) {
        if (projectId == null || projectId.isBlank()
                || selectedDocumentIds == null || selectedDocumentIds.isEmpty()) {
            log.info("Context skip because projectId or selectedDocumentIds is empty projectId={} selectedCount={}",
                    projectId,
                    selectedDocumentIds == null ? 0 : selectedDocumentIds.size());
            return DocumentContextResult.empty();
        }

        List<String> limitedDocIds = selectedDocumentIds.stream().limit(MAX_SELECTED_DOCS).toList();
        log.info("Context build start projectId={} selectedCount={} limitedDocIds={}",
                projectId,
                selectedDocumentIds.size(),
                limitedDocIds);
        List<DocumentVectorChunk> chunks = documentVectorChunkRepository
                .findByProjectIdAndDocumentIdIn(projectId, limitedDocIds);

        log.info("Context chunk query result projectId={} chunkCount={}", projectId, chunks.size());

        if (chunks.isEmpty()) {
            log.info("Context empty because no chunks found projectId={} docIds={}", projectId, limitedDocIds);
            return DocumentContextResult.empty();
        }

        List<Double> questionEmbedding = openRouterEmbeddingService.embed(question);

        List<ScoredChunk> topChunks = chunks.stream()
                .filter(c -> c.getEmbedding() != null && !c.getEmbedding().isEmpty())
                .map(c -> new ScoredChunk(c, cosineSimilarity(questionEmbedding, c.getEmbedding())))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(Math.max(1, aiProperties.getRetrievalTopK()))
                .toList();

        log.info("Context scoring completed projectId={} candidateChunks={} topChunks={}",
                projectId,
                chunks.size(),
                topChunks.size());

        StringBuilder context = new StringBuilder();
        List<RetrievedDocumentSource> sources = new ArrayList<>();
        for (ScoredChunk scored : topChunks) {
            if (context.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            DocumentVectorChunk c = scored.chunk();
            sources.add(new RetrievedDocumentSource(
                    c.getDocumentId(),
                    c.getFileName(),
                    c.getChunkIndex(),
                    scored.score()));
            context.append("\n--- Document: ")
                    .append(c.getFileName())
                    .append(" #")
                    .append(c.getChunkIndex())
                    .append(" (score=")
                    .append(String.format(Locale.US, "%.3f", scored.score()))
                    .append(") ---\n")
                    .append(c.getChunkText())
                    .append("\n");
        }

        log.info("Context build done projectId={} contextChars={}", projectId, context.length());

        return new DocumentContextResult(context.toString(), sources);
    }

    /**
     * Performs upsert document embeddings for document context processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param docId the doc id parameter
     * @param fileName the file name parameter
     * @param content the content parameter
     */
    private void upsertDocumentEmbeddings(String projectId,
            String docId,
            String fileName,
            String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        String normalized = content.replace("\r\n", "\n").trim();
        String contentHash = sha256(normalized);

        List<DocumentVectorChunk> existing = documentVectorChunkRepository
                .findByProjectIdAndDocumentId(projectId, docId);

        if (!existing.isEmpty() && contentHash.equals(existing.get(0).getContentHash())) {
            log.info("Index skip because content hash unchanged projectId={} docId={}", projectId, docId);
            return;
        }

        if (!existing.isEmpty()) {
            documentVectorChunkRepository.deleteByProjectIdAndDocumentId(projectId, docId);
        }

        List<String> textChunks = chunkText(normalized, aiProperties.getChunkSize(), aiProperties.getChunkOverlap());
        log.info("Index chunking result projectId={} docId={} chunkCount={} chunkSize={} chunkOverlap={}",
                projectId,
                docId,
                textChunks.size(),
                aiProperties.getChunkSize(),
                aiProperties.getChunkOverlap());
        List<DocumentVectorChunk> toSave = new ArrayList<>();

        for (int i = 0; i < textChunks.size(); i++) {
            String chunkText = textChunks.get(i);
            List<Double> embedding = openRouterEmbeddingService.embed(chunkText);

            DocumentVectorChunk chunk = new DocumentVectorChunk();
            chunk.setId(UUID.randomUUID().toString());
            chunk.setProjectId(projectId);
            chunk.setDocumentId(docId);
            chunk.setFileName(fileName);
            chunk.setContentHash(contentHash);
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunkText);
            chunk.setEmbedding(embedding);
            chunk.setUpdatedAt(Instant.now());
            toSave.add(chunk);
        }

        documentVectorChunkRepository.saveAll(toSave);
        log.info("Indexed document {} with {} chunks", docId, toSave.size());
    }

    /**
     * Returns is supported for embedding for document context processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param fileName the file name parameter
     * @param fileType the file type parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isSupportedForEmbedding(String fileName, String fileType) {
        String lowerFileName = fileName != null ? fileName.toLowerCase() : "";
        String lowerFileType = fileType != null ? fileType.toLowerCase() : "";
        return lowerFileName.endsWith(".txt")
                || lowerFileName.endsWith(".pdf")
                || lowerFileName.endsWith(".docx")
                || lowerFileType.contains("text")
                || lowerFileType.contains("pdf")
                || lowerFileType.contains("wordprocessingml")
                || lowerFileType.contains("json")
                || lowerFileType.contains("xml")
                || lowerFileType.contains("markdown");
    }

    /**
     * Returns extract text content for document context processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param contentBytes the content bytes parameter
     * @param fileName the file name parameter
     * @param fileType the file type parameter
     * @return the extract text content result
     * @throws IllegalStateException if the requested operation cannot be completed
     */
    private String extractTextContent(byte[] contentBytes, String fileName, String fileType) {
        if (contentBytes == null || contentBytes.length == 0) {
            return "";
        }

        String lowerFileName = fileName != null ? fileName.toLowerCase() : "";
        String lowerFileType = fileType != null ? fileType.toLowerCase() : "";

        try {
            if (lowerFileName.endsWith(".pdf") || lowerFileType.contains("pdf")) {
                try (PDDocument document = Loader.loadPDF(contentBytes)) {
                    return new PDFTextStripper().getText(document);
                }
            }

            if (lowerFileName.endsWith(".docx") || lowerFileType.contains("wordprocessingml")) {
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(contentBytes));
                        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText();
                }
            }

            return new String(contentBytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to extract text from file " + fileName + " (type=" + fileType + ")",
                    e);
        }
    }

    /**
     * Returns chunk text for document context processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param text the text parameter
     * @param chunkSize the chunk size parameter
     * @param chunkOverlap the chunk overlap parameter
     * @return the matching result collection
     */
    private List<String> chunkText(String text, int chunkSize, int chunkOverlap) {
        if (text.isBlank()) {
            return List.of();
        }
        if (text.length() <= chunkSize) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int safeOverlap = Math.max(0, Math.min(chunkOverlap, chunkSize - 1));

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start = end - safeOverlap;
        }

        return chunks;
    }

    /**
     * Returns cosine similarity for document context processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param a the a parameter
     * @param b the b parameter
     * @return the cosine similarity result
     */
    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return -1.0;
        }

        int size = Math.min(a.size(), b.size());
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < size; i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        if (normA == 0.0 || normB == 0.0) {
            return -1.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Returns sha256 for document context processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param content the content parameter
     * @return the sha256 result
     * @throws IllegalStateException if the requested operation cannot be completed
     */
    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record ScoredChunk(DocumentVectorChunk chunk, double score) {
    }
}
