package com.iems.documentservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class ObjectStorageService {

    private final Cloudinary cloudinary;

    public ObjectStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Upload một file lên Cloudinary.
     * Tất cả file đều dùng resource_type = "raw" để hỗ trợ mọi loại file.
     * public_id = objectKey (giữ nguyên cấu trúc thư mục và tên file).
     */
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) throws Exception {
        Path tempFile = Files.createTempFile("iems-upload-", ".tmp");
        try {
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            uploadFile(objectKey, tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public void upload(String objectKey, MultipartFile file) throws Exception {
        Path tempFile = Files.createTempFile("iems-upload-", ".tmp");
        try {
            file.transferTo(tempFile);
            uploadFile(objectKey, tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Tải xuống file từ Cloudinary bằng cách lấy URL công khai rồi mở stream HTTP.
     */
    public InputStream download(String objectKey) throws Exception {
        String url = buildPublicUrl(objectKey);
        return URI.create(url).toURL().openStream();
    }

    /**
     * Xóa file khỏi Cloudinary.
     * invalidate = true để xóa cache CDN.
     */
    public void delete(String objectKey) throws Exception {
        cloudinary.uploader().destroy(objectKey, ObjectUtils.asMap(
                "resource_type", "raw",
                "invalidate", true
        ));
    }

    /**
     * Trả về URL download của file.
     * Vì file được upload với type "upload" (public) nên URL CDN đã có thể truy cập trực tiếp.
     * Nếu cần kiểm soát truy cập, đổi upload type sang "authenticated" và dùng privateDownload.
     */
    public String presignGetUrl(String objectKey) throws Exception {
        return buildPublicUrl(objectKey);
    }

    /**
     * Trả về URL công khai trên CDN Cloudinary.
     * Dạng: https://res.cloudinary.com/{cloud_name}/raw/upload/{objectKey}
     */
    public String buildPublicUrl(String objectKey) {
        return cloudinary.url()
                .resourceType("raw")
                .type("upload")
                .generate(objectKey);
    }

    /**
     * Upload file và trả về URL công khai ngay sau khi upload.
     * Tiện lợi khi cần URL ngay lập tức không cần gọi buildPublicUrl riêng.
     */
    public String uploadAndGetUrl(String objectKey, InputStream inputStream, long size, String contentType) throws Exception {
        Path tempFile = Files.createTempFile("iems-upload-", ".tmp");
        try {
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return uploadFileAndGetUrl(objectKey, tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public String uploadAndGetUrl(String objectKey, MultipartFile file) throws Exception {
        Path tempFile = Files.createTempFile("iems-upload-", ".tmp");
        try {
            file.transferTo(tempFile);
            return uploadFileAndGetUrl(objectKey, tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void uploadFile(String objectKey, Path file) throws Exception {
        cloudinary.uploader().upload(file.toFile(), ObjectUtils.asMap(
                "public_id", objectKey,
                "resource_type", "raw",
                "overwrite", true,
                "invalidate", true
        ));
    }

    @SuppressWarnings("unchecked")
    private String uploadFileAndGetUrl(String objectKey, Path file) throws Exception {
        Map<String, Object> result = cloudinary.uploader().upload(file.toFile(), ObjectUtils.asMap(
                "public_id", objectKey,
                "resource_type", "raw",
                "overwrite", true,
                "invalidate", true
        ));
        return (String) result.get("secure_url");
    }
}
