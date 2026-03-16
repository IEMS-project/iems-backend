package com.iems.projectservice.entity;

import com.iems.projectservice.entity.enums.PermissionGrantType;
import com.iems.projectservice.entity.enums.ProjectPermission;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "member_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "account_id", "permission"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 50)
    private ProjectPermission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private PermissionGrantType type;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
