package com.resumeai.domain;

import com.resumeai.domain.enums.CompanyStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    private String industry;
    private String website;

    @Column(name = "company_size")
    private String companySize;

    private String location;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "logo_path", length = 500)
    private String logoPath;

    @Column(name = "cover_path", length = 500)
    private String coverPath;

    @Column(length = 200)
    private String tagline;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(columnDefinition = "text")
    private String mission;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "company_values")
    private List<String> values;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> benefits;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "twitter_url")
    private String twitterUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CompanyStatus status = CompanyStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
