package com.resumeai.domain;

import com.resumeai.domain.enums.DocumentKind;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One file in a candidate's document library.
 *
 * <p>A library entry is <em>not</em> the evidence behind an application. Applying copies
 * these bytes to the application's own path (see V11), so a candidate may freely rename,
 * replace or delete a document here without touching what a recruiter already screened.
 */
@Entity
@Table(name = "candidate_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentKind kind;

    /** The candidate's own name for it; renaming changes only this. */
    @Column(nullable = false, length = 150)
    private String label;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** SHA-256 of the bytes, so an identical re-upload can be recognised. */
    @Column(length = 64)
    private String checksum;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
