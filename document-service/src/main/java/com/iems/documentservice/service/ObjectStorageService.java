package com.iems.documentservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class ObjectStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String region;
    private final String publicBaseUrl;
    private final Duration presignedUrlExpiration;

    /**
     * Creates a new object storage service instance.
     *
     * @param s3Client the s3 client parameter
     * @param s3Presigner the s3 presigner parameter
     * @param bucket the bucket parameter
     * @param region the region parameter
     * @param publicBaseUrl the public base url parameter
     * @param expirationMinutes the expiration minutes parameter
     */
    public ObjectStorageService(S3Client s3Client,
                                S3Presigner s3Presigner,
                                @Value("${storage.s3.bucket}") String bucket,
                                @Value("${storage.s3.region}") String region,
                                @Value("${storage.s3.public-base-url:}") String publicBaseUrl,
                                @Value("${storage.s3.presigned-url-expiration-minutes:15}") long expirationMinutes) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.region = region;
        this.publicBaseUrl = publicBaseUrl;
        this.presignedUrlExpiration = Duration.ofMinutes(expirationMinutes);
    }

    /**
     * Generates object storage data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     * @param timestamp the timestamp parameter
     * @return the generate upload signature result
     */
    public Map<String, Object> generateUploadSignature(String objectKey, long timestamp) {
        return generateUploadSignature(objectKey, timestamp, null);
    }

    /**
     * Generates object storage data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     * @param timestamp the timestamp parameter
     * @param contentType the content type parameter
     * @return the generate upload signature result
     */
    public Map<String, Object> generateUploadSignature(String objectKey, long timestamp, String contentType) {
        ensureBucketConfigured();
        PutObjectRequest.Builder putObjectRequestBuilder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey);
        if (contentType != null && !contentType.isBlank()) {
            putObjectRequestBuilder.contentType(contentType);
        }
        PutObjectRequest putObjectRequest = putObjectRequestBuilder.build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(presignedUrlExpiration)
                .putObjectRequest(putObjectRequest)
                .build();

        Map<String, Object> response = new HashMap<>();
        response.put("uploadUrl", s3Presigner.presignPutObject(presignRequest).url().toString());
        response.put("method", "PUT");
        response.put("bucket", bucket);
        response.put("objectKey", objectKey);
        response.put("publicId", objectKey);
        response.put("timestamp", timestamp);
        response.put("expiresInSeconds", presignedUrlExpiration.toSeconds());
        response.put("contentType", contentType);
        return response;
    }

    /**
     * Uploads object storage content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     * @param inputStream the input stream parameter
     * @param size the size parameter
     * @param contentType the content type parameter
     */
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        ensureBucketConfigured();
        PutObjectRequest request = putObjectRequest(objectKey, contentType, size);
        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));
    }

    /**
     * Uploads object storage content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     * @param file the file parameter
     * @throws Exception if the requested operation cannot be completed
     */
    public void upload(String objectKey, MultipartFile file) throws Exception {
        ensureBucketConfigured();
        Path tempFile = Files.createTempFile("iems-s3-upload-", ".tmp");
        try {
            file.transferTo(tempFile);
            PutObjectRequest request = putObjectRequest(objectKey, file.getContentType(), file.getSize());
            s3Client.putObject(request, RequestBody.fromFile(tempFile));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Downloads object storage content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     * @return the download result
     */
    public InputStream download(String objectKey) {
        ensureBucketConfigured();
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        ResponseInputStream<GetObjectResponse> objectStream = s3Client.getObject(request);
        return objectStream;
    }

    /**
     * Deletes object storage data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     */
    public void delete(String objectKey) {
        ensureBucketConfigured();
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        executeWithRetry(() -> {
            s3Client.deleteObject(request);
            return null;
        });
    }

    /**
     * Returns presign get url for object storage processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     * @return the presign get url result
     */
    public String presignGetUrl(String objectKey) {
        ensureBucketConfigured();
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(presignedUrlExpiration)
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Builds object storage data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     * @return the build public url result
     */
    public String buildPublicUrl(String objectKey) {
        ensureBucketConfigured();
        if (!publicBaseUrl.isBlank()) {
            return trimTrailingSlash(publicBaseUrl) + "/" + encodeKey(objectKey);
        }

        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + encodeKey(objectKey);
    }

    /**
     * Uploads object storage content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     * @param inputStream the input stream parameter
     * @param size the size parameter
     * @param contentType the content type parameter
     * @return the upload and get url result
     */
    public String uploadAndGetUrl(String objectKey, InputStream inputStream, long size, String contentType) {
        upload(objectKey, inputStream, size, contentType);
        return buildPublicUrl(objectKey);
    }

    /**
     * Uploads object storage content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     * @param file the file parameter
     * @return the upload and get url result
     * @throws Exception if the requested operation cannot be completed
     */
    public String uploadAndGetUrl(String objectKey, MultipartFile file) throws Exception {
        upload(objectKey, file);
        return buildPublicUrl(objectKey);
    }

    /**
     * Stores object storage data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     * @param contentType the content type parameter
     * @param size the size parameter
     * @return the put object request result
     */
    private PutObjectRequest putObjectRequest(String objectKey, String contentType, long size) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentLength(size);

        if (contentType != null && !contentType.isBlank()) {
            builder.contentType(contentType);
        }

        return builder.build();
    }

    /**
     * Ensures that object storage requirements are satisfied.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @throws IllegalStateException if the requested operation cannot be completed
     */
    private void ensureBucketConfigured() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("S3 bucket is not configured. Set AWS_S3_BUCKET or storage.s3.bucket.");
        }
    }

    /**
     * Executes the object storage operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param action the action parameter
     * @return the execute with retry result
     */
    private <T> T executeWithRetry(RetryableAction<T> action) {
        int maxAttempts = 3;
        long backoffMs = 500;
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.execute();
            } catch (RuntimeException e) {
                lastException = e;
                if (attempt == maxAttempts) {
                    break;
                }
                System.err.println("S3 client transient error (attempt " + attempt + "): " + e.getMessage() + ". Retrying in " + backoffMs + "ms...");
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                backoffMs *= 2;
            }
        }
        throw lastException;
    }

    /**
     * Returns trim trailing slash for object storage processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the trim trailing slash result
     */
    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * Returns encode key for object storage processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param objectKey the object key parameter
     * @return the encode key result
     */
    private String encodeKey(String objectKey) {
        String[] parts = objectKey.split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20");
        }
        return String.join("/", parts);
    }

    @FunctionalInterface
    private interface RetryableAction<T> {
        T execute();
    }
}
