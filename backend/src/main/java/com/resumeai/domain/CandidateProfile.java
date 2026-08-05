package com.resumeai.domain;

import com.resumeai.domain.enums.Availability;
import com.resumeai.domain.enums.WorkArrangement;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A candidate's professional identity: the part a recruiter is meant to see.
 *
 * <p>Repeating groups (education, experience, certifications, languages) are JSONB
 * rather than child tables. They are only ever read and written as a whole block
 * for one candidate, never queried across candidates, so a table per group would
 * add joins and migrations without buying anything. Skills are the exception that
 * proves the rule — those are already matched as JSONB in screening_results.
 */
@Entity
@Table(name = "candidate_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateProfile {

    /** Shares the user's id: one profile per candidate. */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 200)
    private String headline;

    @Column(columnDefinition = "text")
    private String summary;

    /** Right to work, e.g. "EU work permit". A bona fide job requirement. */
    @Column(name = "work_authorization", length = 120)
    private String workAuthorization;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "languages")
    private List<Map<String, Object>> languages;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills")
    private List<String> skills;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "education")
    private List<Map<String, Object>> education;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "experience")
    private List<Map<String, Object>> experience;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "certifications")
    private List<Map<String, Object>> certifications;

    @Column(name = "portfolio_url", length = 255)
    private String portfolioUrl;

    @Column(name = "github_url", length = 255)
    private String githubUrl;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Column(name = "salary_min")
    private BigDecimal salaryMin;

    @Column(name = "salary_max")
    private BigDecimal salaryMax;

    @Column(name = "salary_currency", length = 10)
    @Builder.Default
    private String salaryCurrency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "work_arrangement", length = 20)
    private WorkArrangement workArrangement;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", length = 30)
    private Availability availability;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_categories")
    private List<String> preferredCategories;

    @Column(name = "open_to_relocation", nullable = false)
    @Builder.Default
    private boolean openToRelocation = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
