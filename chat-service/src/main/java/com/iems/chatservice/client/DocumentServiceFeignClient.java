package com.iems.chatservice.client;

import com.iems.chatservice.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;


@FeignClient(
        name = "DOCUMENT-SERVICE",
        configuration = FeignClientConfig.class
)
public interface DocumentServiceFeignClient {

    @PostMapping(value = "/api/files/upload-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<java.util.Map<String, Object>> uploadFiles(@RequestPart("files") MultipartFile[] files);

    @PostMapping(value = "/api/files/upload-chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<java.util.Map<String, Object>> uploadChatFiles(
            @RequestPart("conversationId") String conversationId,
            @RequestPart("files") MultipartFile[] files);
}


