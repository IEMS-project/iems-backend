package com.iems.projectservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.AccountIdsDto;
import com.iems.projectservice.dto.response.UserDetailDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Resolves actor display name from IAM service.
 * Calls are made on the REQUEST thread (before @Async handoff)
 * so RequestContextHolder / Feign auth forwarding works correctly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActorNameResolver {

    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;

    /**
     * Returns "FirstName LastName" for the given accountId, or null if unavailable.
     * Safe to call — never throws.
     */
    public String resolve(UUID accountId) {
        if (accountId == null) return null;
        try {
            ResponseEntity<Map<String, Object>> response =
                    userServiceFeignClient.getUsersByAccountIds(
                            new AccountIdsDto(Set.of(accountId)));

            if (response.getBody() == null || response.getBody().get("data") == null) return null;

            List<UserDetailDto> users = objectMapper.convertValue(
                    response.getBody().get("data"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, UserDetailDto.class));

            return users.stream()
                    .filter(u -> accountId.equals(u.getId()))
                    .findFirst()
                    .map(u -> buildName(u.getFirstName(), u.getLastName()))
                    .orElse(null);

        } catch (Exception e) {
            log.debug("Could not resolve actor name for {}: {}", accountId, e.getMessage());
            return null;
        }
    }

    /**
     * Builds actor name data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param first the first parameter
     * @param last the last parameter
     * @return the build name result
     */
    private String buildName(String first, String last) {
        String f = first != null ? first.trim() : "";
        String l = last  != null ? last.trim()  : "";
        String full = (f + " " + l).trim();
        return full.isEmpty() ? null : full;
    }
}
