package com.iems.documentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "document_activity")
public class DocumentActivity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    private UUID id;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType; // FILE | FOLDER

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "payload", length = 4000)
    private String payload;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
