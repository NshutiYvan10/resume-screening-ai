package com.resumeai.repository;

import com.resumeai.domain.Interview;
import com.resumeai.domain.enums.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findByApplicationIdOrderByScheduledAtAsc(UUID applicationId);

    long countByApplicationIdAndStatus(UUID applicationId, InterviewStatus status);

    /** Interviews whose scheduled time has passed but panel feedback is still missing. */
    @Query("""
            SELECT DISTINCT i FROM Interview i JOIN i.panel p
            WHERE i.status <> com.resumeai.domain.enums.InterviewStatus.CANCELLED
              AND i.scheduledAt < :cutoff
              AND NOT EXISTS (
                  SELECT f FROM InterviewFeedback f
                  WHERE f.interview = i AND f.interviewer = p)
            """)
    List<Interview> findWithPendingFeedback(@Param("cutoff") Instant cutoff);
}
