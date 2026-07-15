package com.resumeai.repository;

import com.resumeai.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT l FROM AuditLog l
            WHERE (:companyId IS NULL OR l.companyId = :companyId)
              AND (:action IS NULL OR l.action = :action)
              AND (:search IS NULL OR lower(l.actorEmail) LIKE lower(concat('%', cast(:search as string), '%'))
                   OR lower(l.entityType) LIKE lower(concat('%', cast(:search as string), '%')))
              AND (CAST(:from AS timestamp) IS NULL OR l.createdAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR l.createdAt <= :to)
            """)
    Page<AuditLog> search(@Param("companyId") UUID companyId,
                          @Param("action") String action,
                          @Param("search") String search,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
