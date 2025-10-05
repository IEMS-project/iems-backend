package com.iems.documentservice.client;

import com.iems.documentservice.config.FeignClientConfig;
import com.iems.documentservice.dto.request.UpdateGroupAvatarRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "CHAT-SERVICE",
        configuration = FeignClientConfig.class
)
public interface ChatServiceFeignClient {

    @PutMapping("/api/groups/{groupId}/avatar")
    ResponseEntity<Object> updateGroupAvatar(@PathVariable("groupId") String groupId,
                                             @RequestBody UpdateGroupAvatarRequest body);
}





