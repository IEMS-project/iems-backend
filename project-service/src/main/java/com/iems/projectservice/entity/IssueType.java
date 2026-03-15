package com.iems.projectservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "issue_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
