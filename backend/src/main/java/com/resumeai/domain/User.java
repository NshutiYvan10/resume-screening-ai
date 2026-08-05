package com.resumeai.domain;

import com.resumeai.domain.enums.Role;
import com.resumeai.domain.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    // ---- profile (V9): shared across roles, plus two recruiter-only fields ----

    /** Storage-root-relative key; exposed as a URL, never raw. */
    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @Column(name = "job_title", length = 150)
    private String jobTitle;

    @Column(length = 120)
    private String department;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(length = 200)
    private String location;

    @Column(name = "time_zone", length = 60)
    private String timeZone;

    @Column(length = 20)
    private String locale;

    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    /** Recruiter areas of specialization, e.g. ["Backend", "Data"]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specializations")
    private List<String> specializations;

    @Column(name = "years_experience")
    private BigDecimal yearsExperience;

    /** Stamped the first time the role's required profile fields are all present. */
    @Column(name = "profile_completed_at")
    private Instant profileCompletedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
