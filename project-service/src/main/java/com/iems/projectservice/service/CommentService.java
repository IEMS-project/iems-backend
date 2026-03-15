package com.iems.projectservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.AccountIdsDto;
import com.iems.projectservice.dto.response.CommentResponseDto;
import com.iems.projectservice.dto.response.UserDetailDto;
import com.iems.projectservice.entity.Comment;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;

    public Comment addComment(UUID issueId, UUID authorId, String content, UUID parentCommentId) {
        Comment comment = new Comment();
        comment.setIssueId(issueId);
        comment.setAuthorId(authorId);
        comment.setContent(content);
        comment.setParentCommentId(parentCommentId);
        return commentRepository.save(comment);
    }

    public Comment updateComment(UUID commentId, String content, UUID userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getAuthorId().equals(userId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        comment.setContent(content);
        return commentRepository.save(comment);
    }

    public void deleteComment(UUID commentId, UUID userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getAuthorId().equals(userId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        commentRepository.delete(comment);
    }

    public List<CommentResponseDto> getCommentsByIssue(UUID issueId) {
        List<Comment> comments = commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId);
        if (comments.isEmpty()) return List.of();

        // Batch-fetch author info from IAM
        Set<UUID> authorIds = comments.stream().map(Comment::getAuthorId).collect(Collectors.toSet());
        Map<UUID, UserDetailDto> userMap = new HashMap<>();
        try {
            ResponseEntity<Map<String, Object>> response =
                    userServiceFeignClient.getUsersByAccountIds(new AccountIdsDto(authorIds));
            if (response.getBody() != null && response.getBody().get("data") != null) {
                List<UserDetailDto> users = objectMapper.convertValue(
                        response.getBody().get("data"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, UserDetailDto.class));
                userMap = users.stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserDetailDto::getId, u -> u));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user details for comments: {}", e.getMessage());
        }

        final Map<UUID, UserDetailDto> finalUserMap = userMap;
        return comments.stream().map(c -> {
            UserDetailDto author = finalUserMap.get(c.getAuthorId());
            String name = author != null
                    ? (trim(author.getFirstName()) + " " + trim(author.getLastName())).trim()
                    : null;
            return CommentResponseDto.builder()
                    .id(c.getId())
                    .issueId(c.getIssueId())
                    .authorId(c.getAuthorId())
                    .authorName(name)
                    .authorEmail(author != null ? author.getEmail() : null)
                    .authorImage(author != null ? author.getImage() : null)
                    .content(c.getContent())
                    .parentCommentId(c.getParentCommentId())
                    .createdAt(c.getCreatedAt())
                    .updatedAt(c.getUpdatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    private String trim(String s) {
        return s != null ? s.trim() : "";
    }
}
