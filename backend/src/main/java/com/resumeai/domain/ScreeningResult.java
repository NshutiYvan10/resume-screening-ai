package com.resumeai.domain;

import com.resumeai.domain.enums.ScreeningStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "screening_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningResult {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ScreeningStatus status = ScreeningStatus.PENDING;

    @Column(name = "match_score")
    private BigDecimal matchScore;

    @Column(name = "skills_score")
    private BigDecimal skillsScore;

    @Column(name = "experience_score")
    private BigDecimal experienceScore;

    @Column(name = "education_score")
    private BigDecimal educationScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_skills")
    private List<String> extractedSkills;

    @Column(name = "extracted_education")
    private String extractedEducation;

    @Column(name = "extracted_experience_years")
    private BigDecimal extractedExperienceYears;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_skills")
    private List<String> matchedSkills;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_required")
    private List<String> missingRequired;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_optional")
    private List<String> missingOptional;

    @Column(columnDefinition = "text")
    private String reasoning;

    @Column(name = "parse_quality", length = 10)
    private String parseQuality;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parse_warnings")
    private List<String> parseWarnings;

    @Column(name = "bias_flag", nullable = false)
    @Builder.Default
    private boolean biasFlag = false;

    @Column(name = "bias_flag_reason", columnDefinition = "text")
    private String biasFlagReason;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "screened_at")
    private Instant screenedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
