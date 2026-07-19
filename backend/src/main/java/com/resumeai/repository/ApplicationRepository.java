package com.resumeai.repository;

import com.resumeai.domain.Application;
import com.resumeai.domain.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByJobIdAndCandidateId(UUID jobId, UUID candidateId);

    Optional<Application> findByJobIdAndCandidateId(UUID jobId, UUID candidateId);

    Page<Application> findByCandidateId(UUID candidateId, Pageable pageable);

    @Query("""
            SELECT a FROM Application a
            LEFT JOIN a.screeningResult sr
            WHERE a.job.id = :jobId
              AND (:status IS NULL OR a.status = :status)
              AND (:minScore IS NULL OR sr.matchScore >= :minScore)
            """)
    Page<Application> searchJobApplications(@Param("jobId") UUID jobId,
                                            @Param("status") ApplicationStatus status,
                                            @Param("minScore") BigDecimal minScore,
                                            Pageable pageable);

    long countByJobId(UUID jobId);

    long countByJobIdAndStatus(UUID jobId, ApplicationStatus status);

    /** Company-wide pipeline for admin oversight (across all jobs). */
    @Query("""
            SELECT a FROM Application a
            LEFT JOIN a.screeningResult sr
            WHERE a.job.company.id = :companyId
              AND (:status IS NULL OR a.status = :status)
              AND (:jobId IS NULL OR a.job.id = :jobId)
              AND (:minScore IS NULL OR sr.matchScore >= :minScore)
            """)
    Page<Application> searchCompanyApplications(@Param("companyId") UUID companyId,
                                                @Param("status") ApplicationStatus status,
                                                @Param("jobId") UUID jobId,
                                                @Param("minScore") BigDecimal minScore,
                                                Pageable pageable);

    @Query("SELECT a FROM Application a WHERE a.job.company.id = :companyId ORDER BY a.appliedAt DESC")
    List<Application> findAllForCompany(@Param("companyId") UUID companyId);

    @Query("SELECT a.id FROM Application a WHERE a.job.id = :jobId")
    List<UUID> findIdsByJobId(@Param("jobId") UUID jobId);

    @Query("SELECT count(a) FROM Application a WHERE a.job.company.id = :companyId")
    long countByCompany(@Param("companyId") UUID companyId);

    @Query("""
            SELECT a.status AS status, count(a) AS cnt FROM Application a
            WHERE a.job.company.id = :companyId GROUP BY a.status
            """)
    List<StatusCount> countByCompanyGroupByStatus(@Param("companyId") UUID companyId);

    @Query("""
            SELECT avg(sr.matchScore) FROM ScreeningResult sr
            WHERE sr.application.job.company.id = :companyId
              AND sr.status = com.resumeai.domain.enums.ScreeningStatus.COMPLETED
            """)
    BigDecimal averageScoreForCompany(@Param("companyId") UUID companyId);

    // ---------------------------------------------------------- reporting

    long countByCandidateId(UUID candidateId);

    @Query("""
            SELECT a.status AS status, count(a) AS cnt FROM Application a
            WHERE a.candidate.id = :candidateId GROUP BY a.status
            """)
    List<StatusCount> countByCandidateGroupByStatus(@Param("candidateId") UUID candidateId);

    @Query("""
            SELECT avg(sr.matchScore) FROM ScreeningResult sr
            WHERE sr.application.candidate.id = :candidateId
              AND sr.status = com.resumeai.domain.enums.ScreeningStatus.COMPLETED
            """)
    BigDecimal averageScoreForCandidate(@Param("candidateId") UUID candidateId);

    /** Applications for the jobs a specific recruiter created. */
    @Query("SELECT count(a) FROM Application a WHERE a.job.createdBy.id = :userId")
    long countByRecruiter(@Param("userId") UUID userId);

    @Query("""
            SELECT a.status AS status, count(a) AS cnt FROM Application a
            WHERE a.job.createdBy.id = :userId GROUP BY a.status
            """)
    List<StatusCount> countByRecruiterGroupByStatus(@Param("userId") UUID userId);

    @Query("""
            SELECT avg(sr.matchScore) FROM ScreeningResult sr
            WHERE sr.application.job.createdBy.id = :userId
              AND sr.status = com.resumeai.domain.enums.ScreeningStatus.COMPLETED
            """)
    BigDecimal averageScoreForRecruiter(@Param("userId") UUID userId);

    /** Platform-wide application status breakdown. */
    @Query("SELECT a.status AS status, count(a) AS cnt FROM Application a GROUP BY a.status")
    List<StatusCount> countGroupByStatus();

    /** Average days from application to hire for a company (HIRED only). */
    @Query(value = """
            SELECT avg(EXTRACT(EPOCH FROM (a.hired_at - a.applied_at)) / 86400.0)
            FROM applications a JOIN jobs j ON j.id = a.job_id
            WHERE j.company_id = :companyId AND a.status = 'HIRED' AND a.hired_at IS NOT NULL
            """, nativeQuery = true)
    Double averageTimeToHireDays(@Param("companyId") UUID companyId);

    @Query(value = """
            SELECT to_char(date_trunc('month', a.applied_at), 'YYYY-MM') AS m, count(*) AS c
            FROM applications a
            WHERE a.candidate_id = :candidateId
            GROUP BY 1 ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> applicationsByMonthForCandidate(@Param("candidateId") UUID candidateId);

    @Query(value = """
            SELECT to_char(date_trunc('month', a.applied_at), 'YYYY-MM') AS m, count(*) AS c
            FROM applications a JOIN jobs j ON j.id = a.job_id
            WHERE j.company_id = :companyId
            GROUP BY 1 ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> applicationsByMonthForCompany(@Param("companyId") UUID companyId);

    @Query(value = """
            SELECT to_char(date_trunc('month', applied_at), 'YYYY-MM') AS m, count(*) AS c
            FROM applications
            GROUP BY 1 ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> applicationsByMonth();

    interface StatusCount {
        ApplicationStatus getStatus();

        long getCnt();
    }
}
