package com.iems.documentservice.repository;

import com.iems.documentservice.entity.AiDocumentIndexEvent;
import com.iems.documentservice.entity.enums.AiIndexEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiDocumentIndexEventRepository extends JpaRepository<AiDocumentIndexEvent, UUID> {
    List<AiDocumentIndexEvent> findTop100ByStatusOrderByCreatedAtAsc(AiIndexEventStatus status);
}
