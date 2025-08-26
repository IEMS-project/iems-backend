package com.iems.reportservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "report_receivers")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReportReceiver {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(nullable = false)
    private UUID receiverId;

    @Column(nullable = false)
    private boolean isRead = false;

    private LocalDateTime readAt;
}
