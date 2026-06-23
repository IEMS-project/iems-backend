package com.iems.documentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "project_document")
public class ProjectDocument {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "is_folder", nullable = false)
    @Builder.Default
    private Boolean isFolder = false;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "allow_embedded", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean allowEmbedded = false;

    @Column(name = "ai_indexed", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean aiIndexed = false;
}
