package com.iems.documentservice.service;

import com.iems.documentservice.client.UserServiceFeignClient;
import com.iems.documentservice.dto.request.ShareRequest;
import com.iems.documentservice.dto.response.SharedUserResponse;
import com.iems.documentservice.entity.Share;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.entity.enums.Permission;
import com.iems.documentservice.entity.enums.SharePermission;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.exception.DocumentErrorCode;
import com.iems.documentservice.repository.ShareRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ShareService {

    private final ShareRepository shareRepository;
    private final StoredFileRepository storedFileRepository;
    private final UserServiceFeignClient userServiceFeignClient;
    private final PermissionHelper permissionHelper;

    public ShareService(ShareRepository shareRepository,
                        StoredFileRepository storedFileRepository,
                        UserServiceFeignClient userServiceFeignClient,
                        PermissionHelper permissionHelper) {
        this.shareRepository = shareRepository;
        this.storedFileRepository = storedFileRepository;
        this.userServiceFeignClient = userServiceFeignClient;
        this.permissionHelper = permissionHelper;
    }

    @Transactional
    public void shareItem(UUID itemId, String type, ShareRequest request) {
        UUID ownerId = permissionHelper.getCurrentUserId();
        if (!permissionHelper.validateTargetExistsAndOwned(itemId, type, ownerId)) {
            throw new AppException(DocumentErrorCode.FOLDER_NOT_FOUND);
        }
        for (UUID uid : request.getUserIds()) {
            if (!shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(itemId, type, uid)) {
                shareRepository.save(Share.builder()
                        .targetId(itemId)
                        .targetType(type)
                        .sharedWithUserId(uid)
                        .permission(request.getPermission())
                        .createdAt(OffsetDateTime.now())
                        .build());
            }
        }
        // Khi share file riêng lẻ, tự động đổi permission sang SHARED
        if ("FILE".equals(type)) {
            storedFileRepository.findById(itemId)
                    .filter(f -> f.getPermission() == Permission.PRIVATE)
                    .ifPresent(f -> {
                        f.setPermission(Permission.SHARED);
                        storedFileRepository.save(f);
                    });
        }
    }

    @Transactional
    public void unshareItem(UUID itemId, String type, ShareRequest request) {
        UUID ownerId = permissionHelper.getCurrentUserId();
        if (!permissionHelper.validateTargetExistsAndOwned(itemId, type, ownerId)) {
            throw new AppException(DocumentErrorCode.FOLDER_NOT_FOUND);
        }
        for (UUID uid : request.getUserIds()) {
            shareRepository.deleteByTargetIdAndTargetTypeAndSharedWithUserId(itemId, type, uid);
        }
    }

    public List<SharedUserResponse> getSharedUsers(UUID itemId, String type) {
        UUID ownerId = permissionHelper.getCurrentUserId();
        if (!permissionHelper.validateTargetExistsAndOwned(itemId, type, ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        return shareRepository.findByTargetIdAndTargetType(itemId, type).stream()
                .map(share -> {
                    var builder = SharedUserResponse.builder()
                            .shareId(share.getId())
                            .userId(share.getSharedWithUserId())
                            .permission(share.getPermission())
                            .sharedAt(share.getCreatedAt());
                    try {
                        var resp = userServiceFeignClient.getUserById(share.getSharedWithUserId());
                        if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> api = (Map<String, Object>) resp.getBody();
                            if (api.get("data") instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> user = (Map<String, Object>) api.get("data");
                                if (user.get("firstName") != null) builder.firstName(user.get("firstName").toString());
                                if (user.get("lastName") != null) builder.lastName(user.get("lastName").toString());
                                if (user.get("email") != null) builder.email(user.get("email").toString());
                                if (user.get("image") != null) builder.image(user.get("image").toString());
                            }
                        }
                    } catch (Exception ignored) { }
                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public Share updateSharePermission(UUID shareId, SharePermission permission) {
        UUID ownerId = permissionHelper.getCurrentUserId();
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        if (!permissionHelper.validateTargetExistsAndOwned(share.getTargetId(), share.getTargetType(), ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        share.setPermission(permission);
        shareRepository.save(share);
        return share;
    }

    @Transactional
    public Share removeShare(UUID shareId) {
        UUID ownerId = permissionHelper.getCurrentUserId();
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        if (!permissionHelper.validateTargetExistsAndOwned(share.getTargetId(), share.getTargetType(), ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        shareRepository.delete(share);
        return share;
    }
}
