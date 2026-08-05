package com.resumeai.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Voluntary demographic data, kept deliberately apart from {@link CandidateProfile}.
 *
 * <p>Nothing a recruiter can reach loads this entity. It exists so the platform can
 * report on diversity <em>in aggregate</em>; surfacing any of it next to a hiring
 * decision would invite exactly the bias the screening pipeline already flags. Every
 * field is optional, and {@code consentedAt} records that the candidate chose to
 * share rather than being made to.
 *
 * <p>If you are about to add a getter for this on a candidate-facing recruiter DTO:
 * don't. Aggregate it instead.
 */
@Entity
@Table(name = "candidate_demographics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateDemographics {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Free text on purpose: accepted values differ by jurisdiction. */
    @Column(length = 40)
    private String gender;

    @Column(length = 100)
    private String nationality;

    @Column(length = 100)
    private String ethnicity;

    @Column(length = 40)
    private String disability;

    @Column(name = "veteran_status", length = 40)
    private String veteranStatus;

    /** Evidence of a lawful basis for holding this data. */
    @Column(name = "consented_at")
    private Instant consentedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
