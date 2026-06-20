package com.iems.projectservice.repository;

import com.iems.projectservice.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByIssueIdOrderByCreatedAtAsc(UUID issueId);
    List<Comment> findByParentCommentId(UUID parentCommentId);
    void deleteByIssueId(UUID issueId);
    void deleteByIssueIdIn(List<UUID> issueIds);
}
