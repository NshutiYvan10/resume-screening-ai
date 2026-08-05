package com.resumeai.domain;

import com.resumeai.domain.enums.ReportApprovalAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only approval trail for a report: who requested sign-off, who approved or
 * rejected it and when. The trail is the source of truth; {@code Report.approvedBy}
 * only caches the winning entry so the PDF footer can be rendered cheaply.
 */
@Entity
@Table(name = "report_approvals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id")
    private Report report;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportApprovalAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "actor_name", nullable = false, length = 150)
    private String actorName;

    @Column(name = "actor_role", nullable = false, length = 30)
    private String actorRole;

    @Column(columnDefinition = "text")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
