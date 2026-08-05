package com.resumeai.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A reusable cover letter, stored as text.
 *
 * <p>Text rather than a file so it stays searchable and travels the same path the
 * screening pipeline already understands. Applying copies {@link #body} into
 * {@code applications.cover_letter}, so editing a template afterwards never rewrites
 * what a candidate actually submitted.
 */
@Entity
@Table(name = "candidate_cover_letters")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverLetterTemplate {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(columnDefinition = "text", nullable = false)
    private String body;

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
