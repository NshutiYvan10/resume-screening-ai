package com.resumeai.repository;

import com.resumeai.domain.ScreeningResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScreeningResultRepository extends JpaRepository<ScreeningResult, UUID> {

    Optional<ScreeningResult> findByApplicationId(UUID applicationId);

    /**
     * Count screening rows carrying the same resume fingerprint but belonging to a
     * DIFFERENT candidate — i.e. the same resume text submitted by someone else.
     * A candidate re-using their own resume across jobs is excluded by candidate id.
     */
    @Query("""
            SELECT count(sr) FROM ScreeningResult sr
            WHERE sr.resumeFingerprint = :fingerprint
              AND sr.application.candidate.id <> :candidateId
            """)
    long countOtherCandidatesWithFingerprint(@Param("fingerprint") String fingerprint,
                                             @Param("candidateId") UUID candidateId);

    @Query(value = """
            SELECT width_bucket(sr.match_score, 0, 100.0001, 10) AS bucket, count(*) AS cnt
            FROM screening_results sr
            JOIN applications a ON a.id = sr.application_id
            JOIN jobs j ON j.id = a.job_id
            WHERE j.company_id = :companyId AND sr.status = 'COMPLETED'
            GROUP BY bucket ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> scoreDistributionForCompany(@Param("companyId") UUID companyId);

    @Query(value = """
            SELECT skill, count(*) AS cnt FROM (
                SELECT jsonb_array_elements_text(sr.extracted_skills) AS skill
                FROM screening_results sr
                JOIN applications a ON a.id = sr.application_id
                JOIN jobs j ON j.id = a.job_id
                WHERE j.company_id = :companyId AND sr.status = 'COMPLETED'
            ) s GROUP BY skill ORDER BY cnt DESC LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topSkillsForCompany(@Param("companyId") UUID companyId, @Param("limit") int limit);

    @Query(value = """
            SELECT width_bucket(sr.match_score, 0, 100.0001, 10) AS bucket, count(*) AS cnt
            FROM screening_results sr
            JOIN applications a ON a.id = sr.application_id
            WHERE a.candidate_id = :candidateId AND sr.status = 'COMPLETED'
            GROUP BY bucket ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> scoreDistributionForCandidate(@Param("candidateId") UUID candidateId);

    @Query(value = """
            SELECT width_bucket(sr.match_score, 0, 100.0001, 10) AS bucket, count(*) AS cnt
            FROM screening_results sr
            JOIN applications a ON a.id = sr.application_id
            JOIN jobs j ON j.id = a.job_id
            WHERE j.created_by = :userId AND sr.status = 'COMPLETED'
            GROUP BY bucket ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> scoreDistributionForRecruiter(@Param("userId") UUID userId);

    /** Company-wide count of screenings that raised a bias flag (COMPLETED only). */
    @Query("""
            SELECT count(sr) FROM ScreeningResult sr
            WHERE sr.application.job.company.id = :companyId
              AND sr.status = com.resumeai.domain.enums.ScreeningStatus.COMPLETED
              AND sr.biasFlag = true
            """)
    long countBiasFlaggedForCompany(@Param("companyId") UUID companyId);

    /** Platform-wide screening health: rows grouped by status. */
    @Query("SELECT sr.status AS status, count(sr) AS cnt FROM ScreeningResult sr GROUP BY sr.status")
    List<Object[]> countGroupByStatus();
}
