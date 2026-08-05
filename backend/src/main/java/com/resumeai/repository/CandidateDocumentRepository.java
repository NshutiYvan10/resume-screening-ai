package com.resumeai.repository;

import com.resumeai.domain.CandidateDocument;
import com.resumeai.domain.enums.DocumentKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateDocumentRepository extends JpaRepository<CandidateDocument, UUID> {

    List<CandidateDocument> findByUserIdOrderByKindAscCreatedAtDesc(UUID userId);

    Optional<CandidateDocument> findByUserIdAndKindAndIsDefaultTrue(UUID userId, DocumentKind kind);

    long countByUserIdAndKind(UUID userId, DocumentKind kind);

    /**
     * Clear the current default before setting a new one. A partial unique index enforces
     * one default per kind, so without this the insert/update would be rejected.
     */
    @Modifying
    @Query("""
            UPDATE CandidateDocument d SET d.isDefault = false
            WHERE d.user.id = :userId AND d.kind = :kind AND d.isDefault = true
            """)
    void clearDefault(@Param("userId") UUID userId, @Param("kind") DocumentKind kind);

    // ------------------------------------------------------------- insights
    // Everything below is derived from screenings that actually happened. Applications
    // are attributed to a résumé through applications.source_document_id, which only
    // exists for applications submitted from the library - so figures here deliberately
    // exclude one-off uploads rather than guessing at them.

    /**
     * Per-résumé volume and average score. Kept separate from the outcome query below
     * because joining application_events here would fan out the rows and corrupt the
     * average.
     *
     * @return rows of [documentId, applications, screenedApplications, averageMatchScore]
     */
    @Query(value = """
            SELECT a.source_document_id,
                   count(*)                                              AS applications,
                   count(*) FILTER (WHERE sr.status = 'COMPLETED')        AS screened,
                   avg(sr.match_score) FILTER (WHERE sr.status = 'COMPLETED') AS avg_score
            FROM applications a
            LEFT JOIN screening_results sr ON sr.application_id = a.id
            WHERE a.candidate_id = :candidateId AND a.source_document_id IS NOT NULL
            GROUP BY a.source_document_id
            """, nativeQuery = true)
    List<Object[]> documentUsage(@Param("candidateId") UUID candidateId);

    /**
     * How far applications got, read from the event trail rather than the current status:
     * an application that was interviewed and later rejected still counts as an
     * interview, which is the whole point of showing this to a candidate.
     *
     * @return rows of [documentId, interviews, offers]
     */
    @Query(value = """
            SELECT a.source_document_id,
                   count(DISTINCT a.id) FILTER (WHERE e.type = 'INTERVIEW_SCHEDULED') AS interviews,
                   count(DISTINCT a.id) FILTER (WHERE e.type IN ('OFFER_EXTENDED', 'OFFER_ACCEPTED')) AS offers
            FROM applications a
            JOIN application_events e ON e.application_id = a.id
            WHERE a.candidate_id = :candidateId AND a.source_document_id IS NOT NULL
            GROUP BY a.source_document_id
            """, nativeQuery = true)
    List<Object[]> documentOutcomes(@Param("candidateId") UUID candidateId);

    /**
     * The most recent parse verdict per résumé. Latest rather than worst: if the
     * candidate replaced a badly-parsing file, the old verdict no longer describes
     * the file they now hold.
     *
     * @return rows of [documentId, parseQuality]
     */
    @Query(value = """
            SELECT DISTINCT ON (a.source_document_id) a.source_document_id, sr.parse_quality
            FROM applications a
            JOIN screening_results sr ON sr.application_id = a.id
            WHERE a.candidate_id = :candidateId AND a.source_document_id IS NOT NULL
              AND sr.status = 'COMPLETED' AND sr.parse_quality IS NOT NULL
            ORDER BY a.source_document_id, a.applied_at DESC
            """, nativeQuery = true)
    List<Object[]> documentParseQuality(@Param("candidateId") UUID candidateId);

    /** @return rows of [documentId, warning] - distinct extraction warnings per résumé */
    @Query(value = """
            SELECT DISTINCT a.source_document_id, w AS warning
            FROM applications a
            JOIN screening_results sr ON sr.application_id = a.id
            CROSS JOIN LATERAL jsonb_array_elements_text(sr.parse_warnings) AS w
            WHERE a.candidate_id = :candidateId AND a.source_document_id IS NOT NULL
              AND sr.status = 'COMPLETED' AND sr.parse_warnings IS NOT NULL
            """, nativeQuery = true)
    List<Object[]> documentParseWarnings(@Param("candidateId") UUID candidateId);

    /**
     * Required skills the roles asked for that the résumé did not evidence, across every
     * completed screening. This is the actionable half of the insights panel: a skill
     * appearing repeatedly is a gap worth closing, not a one-off mismatch.
     *
     * @return rows of [skill, occurrences]
     */
    @Query(value = """
            SELECT skill, count(*) AS cnt FROM (
                SELECT jsonb_array_elements_text(sr.missing_required) AS skill
                FROM screening_results sr
                JOIN applications a ON a.id = sr.application_id
                WHERE a.candidate_id = :candidateId AND sr.status = 'COMPLETED'
                  AND sr.missing_required IS NOT NULL
            ) s GROUP BY skill ORDER BY cnt DESC, skill LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> recurringSkillGaps(@Param("candidateId") UUID candidateId, @Param("limit") int limit);

    /** @return rows of [skill, occurrences] - skills the screener credited them for */
    @Query(value = """
            SELECT skill, count(*) AS cnt FROM (
                SELECT jsonb_array_elements_text(sr.matched_skills) AS skill
                FROM screening_results sr
                JOIN applications a ON a.id = sr.application_id
                WHERE a.candidate_id = :candidateId AND sr.status = 'COMPLETED'
                  AND sr.matched_skills IS NOT NULL
            ) s GROUP BY skill ORDER BY cnt DESC, skill LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> recurringSkillStrengths(@Param("candidateId") UUID candidateId, @Param("limit") int limit);

    /**
     * Applications that cannot be attributed to any saved résumé - one-off uploads, and
     * anything submitted before the library existed. Surfaced so the panel can say why
     * its totals are lower than the candidate's application count instead of silently
     * disagreeing with the applications page.
     */
    @Query(value = """
            SELECT count(*) FROM applications
            WHERE candidate_id = :candidateId AND source_document_id IS NULL
            """, nativeQuery = true)
    long countUnattributedApplications(@Param("candidateId") UUID candidateId);
}
