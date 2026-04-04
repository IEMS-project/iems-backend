package com.iems.projectservice.client;

import com.iems.projectservice.config.FeignClientConfig;
import com.iems.projectservice.dto.response.DocumentApiResponseDto;
import com.iems.projectservice.dto.SimpleFileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "DOCUMENT-SERVICE",
        configuration = FeignClientConfig.class
)
public interface DocumentServiceFeignClient {

    @PostMapping(value = "/api/files/upload-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentApiResponseDto<List<SimpleFileResponse>>> uploadFiles(
            @RequestParam(required = false) UUID folderId,
            @RequestPart("files") MultipartFile[] files
    );

    @PostMapping(value = "/api/files/upload-public", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentApiResponseDto<List<SimpleFileResponse>>> uploadFilesToPublic(
            @RequestPart("files") MultipartFile[] files
    );

    @DeleteMapping("/api/files/{id}")
    ResponseEntity<DocumentApiResponseDto<Object>> deleteFile(@PathVariable("id") String fileId);

        @PostMapping("/api/projects/{projectId}/documents/folders/init-default")
        ResponseEntity<DocumentApiResponseDto<Object>> initDefaultDocsFolder(
                        @PathVariable("projectId") UUID projectId,
                        @RequestHeader(value = "Authorization", required = false) String authorization
        );


}
