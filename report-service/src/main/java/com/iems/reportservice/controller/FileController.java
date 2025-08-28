package com.iems.reportservice.controller;

import com.iems.reportservice.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@RestController
@RequestMapping("/files")
public class FileController {

    @Autowired
    private FileService fileService;

    /**
     * Upload file lên MinIO
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            fileService.uploadFile(
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            );
            return ResponseEntity.ok("Upload thành công: " + file.getOriginalFilename());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload thất bại: " + e.getMessage());
        }
    }

    /**
     * Download file từ MinIO
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileName) {
        try (InputStream inputStream = fileService.downloadFile(fileName)) {
            byte[] content = inputStream.readAllBytes();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(content);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(null);
        }
    }
}
