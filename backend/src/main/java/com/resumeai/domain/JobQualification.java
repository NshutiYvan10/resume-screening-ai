package com.resumeai.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "job_qualifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobQualification {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id")
    private Job job;

    @Column(nullable = false, length = 150)
    private String skill;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal weight = BigDecimal.ONE;

    @Column(nullable = false)
    @Builder.Default
    private boolean required = false;
}
