package com.resumeai.domain;

import com.resumeai.domain.enums.ApplicationStatus;
import com.resumeai.domain.enums.RejectionReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Application {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id")
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id")
    private User candidate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    @Column(name = "cover_letter", columnDefinition = "text")
    private String coverLetter;

    @Column(name = "resume_file_name", nullable = false)
    private String resumeFileName;

    @Column(name = "resume_stored_path", nullable = false, length = 500)
    private String resumeStoredPath;

    @Column(name = "resume_content_type", length = 100)
    private String resumeContentType;

    @Column(name = "recruiter_note", columnDefinition = "text")
    private String recruiterNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 40)
    private RejectionReason rejectionReason;

    @Column(name = "rejection_note", columnDefinition = "text")
    private String rejectionNote;

    @Column(name = "hired_at")
    private Instant hiredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_updated_by")
    private User statusUpdatedBy;

    @Column(name = "status_updated_at")
    private Instant statusUpdatedAt;

    @OneToOne(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ScreeningResult screeningResult;

    // Set on insert by @CreationTimestamp; also refreshed explicitly when a
    // withdrawn application is re-submitted, so it must remain updatable.
    @CreationTimestamp
    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
