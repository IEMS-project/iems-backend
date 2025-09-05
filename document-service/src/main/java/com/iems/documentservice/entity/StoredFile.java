package com.iems.documentservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "file")
public class StoredFile {

    public enum Permission {
        PRIVATE,
        PUBLIC,
        SHARED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    // path/key in MinIO bucket
    @Column(nullable = false)
    private String path;

    private Long size;

    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission permission;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}



