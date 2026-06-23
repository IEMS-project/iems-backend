package com.iems.projectservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "workflow_transitions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_id", "from_status_id", "to_status_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "from_status_id", nullable = false)
    private UUID fromStatusId;

    @Column(name = "to_status_id", nullable = false)
    private UUID toStatusId;

    @Column(length = 100)
    private String name;
}
