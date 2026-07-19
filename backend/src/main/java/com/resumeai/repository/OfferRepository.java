package com.resumeai.repository;

import com.resumeai.domain.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    Optional<Offer> findByApplicationId(UUID applicationId);

    /** Company-wide offer counts grouped by status: [status, count]. */
    @Query("""
            SELECT o.status, count(o) FROM Offer o
            WHERE o.application.job.company.id = :companyId GROUP BY o.status
            """)
    List<Object[]> countByCompanyGroupByStatus(@Param("companyId") UUID companyId);
}
