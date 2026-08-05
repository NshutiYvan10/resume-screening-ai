package com.resumeai.domain;

import com.resumeai.domain.enums.ReportScope;
import com.resumeai.domain.enums.ReportStatus;
import com.resumeai.domain.enums.ReportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * A generated report document. The row is created first (status QUEUED) and the
 * PDF is rendered asynchronously, so the UI always has something to poll.
 *
 * <p>Prepared-by and approved-by identities are denormalised onto the row because
 * they are printed into the PDF: the document must keep saying who signed it even
 * if that user is later renamed or deactivated.
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue
    private UUID id;

    /** Human-quotable identifier printed on the document, e.g. RPT-2026-000042. */
    @Column(name = "reference_no", nullable = false, length = 30, unique = true)
    private String referenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReportType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.QUEUED;

    @Column(nullable = false, length = 200)
    private String title;

    /** Null for platform-wide and candidate-owned reports. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    /** Whose activity the report is about, when that is narrower than the company. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_user_id")
    private User subjectUser;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    /** Filters the report was generated with, kept so a run can be explained later. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters")
    private Map<String, Object> parameters;

    // ---- prepared by ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by")
    private User generatedBy;

    @Column(name = "generated_by_name", nullable = false, length = 150)
    private String generatedByName;

    @Column(name = "generated_by_role", nullable = false, length = 30)
    private String generatedByRole;

    /** Set when rendering finishes, not when the row is created. */
    @Column(name = "generated_at")
    private Instant generatedAt;

    // ---- rendered artefact ----

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "page_count")
    private Integer pageCount;

    /** SHA-256 of the stored PDF, so a downloaded copy can be proven unaltered. */
    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    // ---- approval ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_by_name", length = 150)
    private String approvedByName;

    @Column(name = "approved_by_role", length = 30)
    private String approvedByRole;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
