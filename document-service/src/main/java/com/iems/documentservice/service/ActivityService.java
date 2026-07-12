package com.iems.documentservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.documentservice.client.UserServiceFeignClient;
import com.iems.documentservice.dto.request.AccountIdsDto;
import com.iems.documentservice.dto.request.UserIdsDto;
import com.iems.documentservice.dto.response.DocumentActivityResponse;
import com.iems.documentservice.entity.DocumentActivity;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.exception.DocumentErrorCode;
import com.iems.documentservice.repository.DocumentActivityRepository;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.ProjectDocumentRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ActivityService {

    private final DocumentActivityRepository documentActivityRepository;
    private final PermissionHelper permissionHelper;
    private final FolderRepository folderRepository;
    private final StoredFileRepository storedFileRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new activity service instance.
     *
     * @param documentActivityRepository the document activity repository parameter
     * @param permissionHelper the permission helper parameter
     * @param folderRepository the folder repository parameter
     * @param storedFileRepository the stored file repository parameter
     * @param projectDocumentRepository the project document repository parameter
     * @param userServiceFeignClient the user service feign client parameter
     * @param objectMapper the object mapper parameter
     */
    public ActivityService(DocumentActivityRepository documentActivityRepository,
                           PermissionHelper permissionHelper,
                           FolderRepository folderRepository,
                           StoredFileRepository storedFileRepository,
                           ProjectDocumentRepository projectDocumentRepository,
                           UserServiceFeignClient userServiceFeignClient,
                           ObjectMapper objectMapper) {
        this.documentActivityRepository = documentActivityRepository;
        this.permissionHelper = permissionHelper;
        this.folderRepository = folderRepository;
        this.storedFileRepository = storedFileRepository;
        this.projectDocumentRepository = projectDocumentRepository;
        this.userServiceFeignClient = userServiceFeignClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Performs log for activity processing.
     *
     * @param targetType the target type parameter
     * @param targetId the target id parameter
     * @param actionKey the action key parameter
     */
    @Transactional
    public void log(String targetType, UUID targetId, String actionKey) {
        log(targetType, targetId, actionKey, null);
    }

    /**
     * Performs log for activity processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param targetType the target type parameter
     * @param targetId the target id parameter
     * @param actionKey the action key parameter
     * @param payload the payload parameter
     */
    @Transactional
    public void log(String targetType, UUID targetId, String actionKey, Map<String, Object> payload) {
        UUID actorUserId = permissionHelper.getCurrentUserId();
        String safeMessage = (actionKey == null || actionKey.isBlank())
            ? "documents.activity.item.updated"
            : actionKey;

        documentActivityRepository.save(DocumentActivity.builder()
                .targetType(normalizeType(targetType))
                .targetId(targetId)
                .action(actionKey)
            .message(safeMessage)
                .payload(toJson(payload))
                .actorUserId(actorUserId)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    /**
     * Lists activity information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param targetId the target id parameter
     * @param targetType the target type parameter
     * @return the matching result collection
     * @throws AppException if a business rule prevents the requested operation
     */
    public List<DocumentActivityResponse> listActivities(UUID targetId, String targetType) {
        String normalizedType = normalizeType(targetType);
        UUID requesterId = permissionHelper.getCurrentUserId();

        if ("FOLDER".equals(normalizedType)) {
            if (folderRepository.existsById(targetId)) {
                permissionHelper.enforceFolderReadPermission(targetId, requesterId);
            } else if (!projectDocumentRepository.existsById(targetId)) {
                throw new AppException(DocumentErrorCode.FOLDER_NOT_FOUND);
            }
            // If it's a project folder, project-scoped permission is checked at controller level via AOP
        } else {
            var fileOpt = storedFileRepository.findById(targetId);
            if (fileOpt.isPresent()) {
                permissionHelper.enforceReadPermission(fileOpt.get(), requesterId);
            } else if (!projectDocumentRepository.existsById(targetId)) {
                throw new AppException(DocumentErrorCode.FILE_NOT_FOUND);
            }
            // If it's a project file, project-scoped permission is checked at controller level via AOP
        }

        List<DocumentActivity> activities = documentActivityRepository.findByTargetIdAndTargetTypeOrderByCreatedAtDesc(targetId, normalizedType);
        Map<UUID, Map<String, Object>> actorMap = getActorMap(activities);

        return activities
                .stream()
                .map(activity -> {
                    Map<String, Object> actor = actorMap.getOrDefault(activity.getActorUserId(), Map.of());
                    return DocumentActivityResponse.builder()
                            .id(activity.getId())
                            .actionKey(activity.getAction())
                            .message(activity.getMessage())
                            .payload(fromJson(activity.getPayload()))
                            .actorUserId(activity.getActorUserId())
                            .actorName(buildActorName(actor))
                            .actorEmail(toStringOrNull(actor.get("email")))
                            .timestamp(activity.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Retrieves activity information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param activities the activities parameter
     * @return the get actor map result
     */
    private Map<UUID, Map<String, Object>> getActorMap(List<DocumentActivity> activities) {
        Set<UUID> actorIds = activities.stream()
                .map(DocumentActivity::getActorUserId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));

        if (actorIds.isEmpty()) return Map.of();

        Map<UUID, Map<String, Object>> result = new HashMap<>();

        // Actor IDs in document-service JWT are account IDs.
        mergeUsers(result, getUsersByAccountIds(actorIds));

        // Backward compatibility: some environments may still use user IDs.
        if (result.size() < actorIds.size()) {
            mergeUsers(result, getUsersByIds(actorIds));
        }

        // Final fallback: query one-by-one by account, then by user ID.
        for (UUID actorId : actorIds) {
            if (result.containsKey(actorId)) continue;
            Map<String, Object> single = getActorByAccountId(actorId);
            if (single.isEmpty()) {
                single = getActorByUserId(actorId);
            }
            if (!single.isEmpty()) {
                result.put(actorId, single);
            }
        }

        return result;
    }

    /**
     * Retrieves activity information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param accountIds the account ids parameter
     * @return the get users by account ids result
     */
    private Map<UUID, Map<String, Object>> getUsersByAccountIds(Set<UUID> accountIds) {
        try {
            var response = userServiceFeignClient.getUsersByAccountIds(AccountIdsDto.builder().accountIds(accountIds).build());
            return extractUsersFromApiResponse(response != null ? response.getBody() : null);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * Retrieves activity information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param userIds the user ids parameter
     * @return the get users by ids result
     */
    private Map<UUID, Map<String, Object>> getUsersByIds(Set<UUID> userIds) {
        try {
            var response = userServiceFeignClient.getUsersByID(UserIdsDto.builder().ids(userIds).build());
            return extractUsersFromApiResponse(response != null ? response.getBody() : null);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * Retrieves activity information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param accountId the account id parameter
     * @return the get actor by account id result
     */
    private Map<String, Object> getActorByAccountId(UUID accountId) {
        try {
            var response = userServiceFeignClient.getUserByAccountId(accountId);
            if (response == null || !response.getStatusCode().is2xxSuccessful()) return Map.of();
            return extractSingleUser(response.getBody());
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * Retrieves activity information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @return the get actor by user id result
     */
    private Map<String, Object> getActorByUserId(UUID userId) {
        try {
            var response = userServiceFeignClient.getUserById(userId);
            if (response == null || !response.getStatusCode().is2xxSuccessful()) return Map.of();
            return extractSingleUser(response.getBody());
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * Returns extract single user for activity processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param body the body parameter
     * @return the extract single user result
     */
    private Map<String, Object> extractSingleUser(Object body) {
        if (!(body instanceof Map<?, ?> bodyMap)) return Map.of();
        Object data = bodyMap.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) return Map.of();
        UUID accountId = parseUuid(dataMap.get("id"));
        if (accountId == null) return Map.of();
        Map<UUID, Map<String, Object>> map = new HashMap<>();
        collectActorRow(map, dataMap);
        return map.getOrDefault(accountId, Map.of());
    }

    /**
     * Returns extract users from api response for activity processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param body the body parameter
     * @return the extract users from api response result
     */
    private Map<UUID, Map<String, Object>> extractUsersFromApiResponse(Object body) {
        if (!(body instanceof Map<?, ?> bodyMap)) return Map.of();
        Object data = bodyMap.get("data");
        Map<UUID, Map<String, Object>> result = new HashMap<>();

        if (data instanceof List<?> dataList) {
            for (Object row : dataList) {
                collectActorRow(result, row);
            }
        } else if (data instanceof Map<?, ?> dataMap) {
            for (Object row : dataMap.values()) {
                collectActorRow(result, row);
            }
        }

        return result;
    }

    /**
     * Performs merge users for activity processing.
     *
     * @param target the target parameter
     * @param source the source parameter
     */
    private void mergeUsers(Map<UUID, Map<String, Object>> target, Map<UUID, Map<String, Object>> source) {
        for (Map.Entry<UUID, Map<String, Object>> entry : source.entrySet()) {
            target.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Performs collect actor row for activity processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param result the result parameter
     * @param row the row parameter
     */
    private void collectActorRow(Map<UUID, Map<String, Object>> result, Object row) {
        if (!(row instanceof Map<?, ?> rowMap)) return;
        UUID userId = parseUuid(rowMap.get("id"));
        if (userId == null) return;

        Map<String, Object> normalized = new HashMap<>();
        normalized.put("firstName", rowMap.get("firstName"));
        normalized.put("lastName", rowMap.get("lastName"));
        normalized.put("fullName", rowMap.get("fullName"));
        normalized.put("name", rowMap.get("name"));
        normalized.put("email", rowMap.get("email"));
        result.put(userId, normalized);
    }

    /**
     * Builds activity data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param actor the actor parameter
     * @return the build actor name result
     */
    private String buildActorName(Map<String, Object> actor) {
        if (actor == null || actor.isEmpty()) return null;
        String firstName = toStringOrNull(actor.get("firstName"));
        String lastName = toStringOrNull(actor.get("lastName"));
        String fullName = toStringOrNull(actor.get("fullName"));
        String name = toStringOrNull(actor.get("name"));

        String fromFirstLast = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        if (!fromFirstLast.isBlank()) return fromFirstLast;
        if (fullName != null && !fullName.isBlank()) return fullName;
        if (name != null && !name.isBlank()) return name;
        return null;
    }

    /**
     * Returns to json for activity processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param payload the payload parameter
     * @return the to json result
     */
    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Returns from json for activity processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param payload the payload parameter
     * @return the from json result
     */
    private Map<String, Object> fromJson(String payload) {
        if (payload == null || payload.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * Parses activity data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the parse uuid result
     */
    private UUID parseUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Returns to string or null for activity processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the to string or null result
     */
    private String toStringOrNull(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        return s.isBlank() ? null : s;
    }

    /**
     * Normalizes activity content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param targetType the target type parameter
     * @return the normalize type result
     * @throws AppException if a business rule prevents the requested operation
     */
    private String normalizeType(String targetType) {
        if (targetType == null) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }
        String normalized = targetType.trim().toUpperCase(Locale.ROOT);
        if (!"FILE".equals(normalized) && !"FOLDER".equals(normalized)) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }
        return normalized;
    }
}
