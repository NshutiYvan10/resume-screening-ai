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
}
