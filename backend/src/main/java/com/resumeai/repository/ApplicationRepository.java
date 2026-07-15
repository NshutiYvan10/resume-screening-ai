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
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByJobIdAndCandidateId(UUID jobId, UUID candidateId);

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

    interface StatusCount {
        ApplicationStatus getStatus();

        long getCnt();
    }
}
