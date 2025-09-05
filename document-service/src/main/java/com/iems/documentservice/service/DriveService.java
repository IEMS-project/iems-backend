package com.iems.documentservice.service;

import com.iems.documentservice.dto.request.CreateFolderRequest;
import com.iems.documentservice.dto.request.UpdatePermissionRequest;
import com.iems.documentservice.dto.request.ShareRequest;
import com.iems.documentservice.dto.response.FileResponse;
import com.iems.documentservice.dto.response.FolderResponse;
import com.iems.documentservice.entity.Folder;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.entity.FileShare;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import com.iems.documentservice.repository.FileShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

@Service
public class DriveService {

    private static final long MAX_UPLOAD_SIZE = 50L * 1024 * 1024; // 50MB

    private final FolderRepository folderRepository;
    private final StoredFileRepository storedFileRepository;
    private final ObjectStorageService storageService;
    private final FileShareRepository fileShareRepository;

    public DriveService(FolderRepository folderRepository,
                        StoredFileRepository storedFileRepository,
                        ObjectStorageService storageService,
                        FileShareRepository fileShareRepository) {
        this.folderRepository = folderRepository;
        this.storedFileRepository = storedFileRepository;
        this.storageService = storageService;
        this.fileShareRepository = fileShareRepository;
    }

    @Transactional
    public FolderResponse createFolder(CreateFolderRequest request) {
        Folder parent = null;
        if (request.getParentId() != null) {
            parent = folderRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent folder not found"));
        }
        Folder folder = Folder.builder()
                .name(request.getName())
                .parent(parent)
                .ownerId(request.getOwnerId())
                .createdAt(OffsetDateTime.now())
                .build();
        folder = folderRepository.save(folder);
        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentId(parent != null ? parent.getId() : null)
                .ownerId(folder.getOwnerId())
                .createdAt(folder.getCreatedAt())
                .build();
    }

    @Transactional
    public FileResponse uploadFile(Long folderId, Long ownerId, MultipartFile file) throws Exception {
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new IllegalArgumentException("File size exceeds 50MB limit");
        }
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        }
        String objectKey = generateObjectKey(folderId, ownerId, file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            storageService.upload(objectKey, in, file.getSize(), file.getContentType());
        }
        StoredFile stored = StoredFile.builder()
                .name(file.getOriginalFilename())
                .folder(folder)
                .ownerId(ownerId)
                .path(objectKey)
                .size(file.getSize())
                .type(file.getContentType())
                .permission(StoredFile.Permission.PRIVATE)
                .createdAt(OffsetDateTime.now())
                .build();
        stored = storedFileRepository.save(stored);
        return toResponse(stored, null);
    }

    public FileResponse downloadInfo(Long fileId, Long requesterId) throws Exception {
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        enforceReadPermission(file, requesterId);
        String presigned = storageService.presignGetUrl(file.getPath());
        return toResponse(file, presigned);
    }

    public InputStream downloadStream(Long fileId, Long requesterId) throws Exception {
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        enforceReadPermission(file, requesterId);
        return storageService.download(file.getPath());
    }

    @Transactional
    public void updatePermission(Long fileId, UpdatePermissionRequest request) {
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        enforceOwner(file, request.getOwnerId());
        file.setPermission(request.getPermission());
        storedFileRepository.save(file);
    }

    @Transactional
    public void deleteFile(Long fileId, Long requesterId) throws Exception {
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        enforceOwner(file, requesterId);
        storageService.delete(file.getPath());
        storedFileRepository.delete(file);
    }

    @Transactional
    public void deleteFolderRecursive(Long folderId, Long requesterId) throws Exception {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        if (!folder.getOwnerId().equals(requesterId)) {
            throw new IllegalStateException("Not allowed");
        }
        // delete files in this folder
        List<StoredFile> files = storedFileRepository.findByFolderId(folder.getId());
        for (StoredFile f : files) {
            storageService.delete(f.getPath());
        }
        storedFileRepository.deleteAll(files);
        // recurse children
        List<Folder> children = folderRepository.findByParentId(folder.getId());
        for (Folder child : children) {
            deleteFolderRecursive(child.getId(), requesterId);
        }
        folderRepository.delete(folder);
    }

    private void enforceOwner(StoredFile file, Long requesterId) {
        if (!file.getOwnerId().equals(requesterId)) {
            throw new IllegalStateException("Not allowed");
        }
    }

    private void enforceReadPermission(StoredFile file, Long requesterId) {
        if (file.getPermission() == StoredFile.Permission.PUBLIC) {
            return;
        }
        if (file.getPermission() == StoredFile.Permission.SHARED) {
            if (requesterId != null && fileShareRepository.existsByFileIdAndSharedWithUserId(file.getId(), requesterId)) {
                return;
            }
        }
        if (!file.getOwnerId().equals(requesterId)) {
            throw new IllegalStateException("Not allowed");
        }
    }

    private String generateObjectKey(Long folderId, Long ownerId, String fileName) {
        String folderPart = folderId != null ? String.valueOf(folderId) : "root";
        long ts = System.currentTimeMillis();
        return "owners/" + ownerId + "/" + folderPart + "/" + ts + "-" + fileName;
    }

    private FileResponse toResponse(StoredFile file, String presignedUrl) {
        return FileResponse.builder()
                .id(file.getId())
                .name(file.getName())
                .folderId(file.getFolder() != null ? file.getFolder().getId() : null)
                .ownerId(file.getOwnerId())
                .path(file.getPath())
                .size(file.getSize())
                .type(file.getType())
                .permission(file.getPermission())
                .createdAt(file.getCreatedAt())
                .presignedUrl(presignedUrl)
                .build();
    }

    public List<FolderResponse> listFolders(Long ownerId) {
        return folderRepository.findByOwnerId(ownerId).stream()
                .map(f -> FolderResponse.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .parentId(f.getParent() != null ? f.getParent().getId() : null)
                        .ownerId(f.getOwnerId())
                        .createdAt(f.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public List<FileResponse> listFiles(Long ownerId) {
        return storedFileRepository.findByOwnerId(ownerId).stream()
                .map(f -> toResponse(f, null))
                .collect(Collectors.toList());
    }

    public List<FileResponse> listFilesInFolder(Long folderId, Long ownerId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        if (!folder.getOwnerId().equals(ownerId)) {
            throw new IllegalStateException("Not allowed");
        }
        return storedFileRepository.findByFolderId(folderId).stream()
                .map(f -> toResponse(f, null))
                .collect(Collectors.toList());
    }

    @Transactional
    public void shareFile(Long fileId, ShareRequest request) {
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        enforceOwner(file, request.getOwnerId());
        // Ensure permission is SHARED
        if (file.getPermission() == StoredFile.Permission.PRIVATE) {
            file.setPermission(StoredFile.Permission.SHARED);
            storedFileRepository.save(file);
        }
        for (Long userId : request.getUserIds()) {
            if (!fileShareRepository.existsByFileIdAndSharedWithUserId(fileId, userId)) {
                FileShare share = FileShare.builder()
                        .file(file)
                        .sharedWithUserId(userId)
                        .createdAt(OffsetDateTime.now())
                        .build();
                fileShareRepository.save(share);
            }
        }
    }

    @Transactional
    public void unshareFile(Long fileId, ShareRequest request) {
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        enforceOwner(file, request.getOwnerId());
        for (Long userId : request.getUserIds()) {
            fileShareRepository.deleteByFileIdAndSharedWithUserId(fileId, userId);
        }
    }

    public List<FileResponse> listAccessibleFiles(Long requesterId) {
        // owned
        List<StoredFile> owned = storedFileRepository.findByOwnerId(requesterId);
        // public
        List<StoredFile> publicFiles = storedFileRepository.findByPermission(StoredFile.Permission.PUBLIC);
        // shared
        List<FileShare> shares = fileShareRepository.findBySharedWithUserId(requesterId);
        Set<Long> sharedIds = shares.stream().map(s -> s.getFile().getId()).collect(Collectors.toSet());
        List<StoredFile> sharedFiles = sharedIds.isEmpty() ? List.of() : storedFileRepository.findByIdIn(sharedIds);

        // merge unique by id
        Set<Long> seen = new HashSet<>();
        return List.of(owned, publicFiles, sharedFiles).stream()
                .flatMap(List::stream)
                .filter(f -> seen.add(f.getId()))
                .map(f -> toResponse(f, null))
                .collect(Collectors.toList());
    }
}


