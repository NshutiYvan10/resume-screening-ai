package com.resumeai.repository;

import com.resumeai.domain.Report;
import com.resumeai.domain.enums.ReportStatus;
import com.resumeai.domain.enums.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReferenceNo(String referenceNo);

    long countByCreatedAtAfter(Instant after);

    /**
     * Report archive with optional filters. Visibility is decided by the caller and
     * passed in as concrete ids: {@code companyId} scopes to a tenant and
     * {@code generatedById} narrows to the caller's own documents.
     */
    @Query("""
            SELECT r FROM Report r
            WHERE (:companyId IS NULL OR r.company.id = :companyId)
              AND (:generatedById IS NULL OR r.generatedBy.id = :generatedById)
              AND (:type IS NULL OR r.type = :type)
              AND (:status IS NULL OR r.status = :status)
              AND (CAST(:from AS timestamp) IS NULL OR r.createdAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR r.createdAt <= :to)
            """)
    Page<Report> search(@Param("companyId") UUID companyId,
                        @Param("generatedById") UUID generatedById,
                        @Param("type") ReportType type,
                        @Param("status") ReportStatus status,
                        @Param("from") Instant from,
                        @Param("to") Instant to,
                        Pageable pageable);
}
