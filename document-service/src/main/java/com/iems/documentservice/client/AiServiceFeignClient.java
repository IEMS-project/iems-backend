package com.iems.documentservice.client;

import com.iems.documentservice.dto.request.AiIndexCommandRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "AI-SERVICE")
public interface AiServiceFeignClient {

    @PostMapping("/api/ai/indexing/events")
    void dispatchIndexCommand(@RequestBody AiIndexCommandRequest request);
}
