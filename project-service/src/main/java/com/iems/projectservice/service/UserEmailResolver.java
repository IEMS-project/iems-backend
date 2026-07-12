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
 * Resolves email + display name for a user by accountId.
 * Wraps UserServiceFeignClient – safe (never throws).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserEmailResolver {

    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;

    public record UserInfo(String email, String displayName) {}

    /**
     * Returns email + displayName for a single accountId.
     * Returns null email/name if lookup fails.
     */
    public UserInfo resolve(UUID accountId) {
        if (accountId == null) return new UserInfo(null, null);
        try {
            ResponseEntity<Map<String, Object>> response =
                    userServiceFeignClient.getUsersByAccountIds(
                            new AccountIdsDto(Set.of(accountId)));

            if (response.getBody() == null || response.getBody().get("data") == null)
                return new UserInfo(null, null);

            List<UserDetailDto> users = objectMapper.convertValue(
                    response.getBody().get("data"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, UserDetailDto.class));

            return users.stream()
                    .filter(u -> accountId.equals(u.getId()))
                    .findFirst()
                    .map(u -> new UserInfo(u.getEmail(), buildName(u.getFirstName(), u.getLastName())))
                    .orElse(new UserInfo(null, null));

        } catch (Exception e) {
            log.debug("Could not resolve email for accountId {}: {}", accountId, e.getMessage());
            return new UserInfo(null, null);
        }
    }

    /**
     * Batch resolves for multiple accountIds → Map<accountId, UserInfo>
     */
    public Map<UUID, UserInfo> resolveAll(Set<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return Collections.emptyMap();
        try {
            ResponseEntity<Map<String, Object>> response =
                    userServiceFeignClient.getUsersByAccountIds(new AccountIdsDto(accountIds));

            if (response.getBody() == null || response.getBody().get("data") == null)
                return Collections.emptyMap();

            List<UserDetailDto> users = objectMapper.convertValue(
                    response.getBody().get("data"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, UserDetailDto.class));

            Map<UUID, UserInfo> result = new HashMap<>();
            for (UserDetailDto u : users) {
                if (u.getId() != null) {
                    result.put(u.getId(), new UserInfo(u.getEmail(), buildName(u.getFirstName(), u.getLastName())));
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("Could not batch-resolve emails: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Builds user email data for downstream processing.
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
