package com.iems.projectservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "project_allowed_roles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "role_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAllowedRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "role_id", nullable = false)
    private UUID roleId; // from user-service Role.id

    @Column(name = "role_name", nullable = false, length = 150)
    private String roleName; // snapshot of Role.name for display
}


