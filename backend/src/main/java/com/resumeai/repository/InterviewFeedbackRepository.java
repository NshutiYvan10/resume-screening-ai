package com.resumeai.repository;

import com.resumeai.domain.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, UUID> {

    boolean existsByInterviewIdAndInterviewerId(UUID interviewId, UUID interviewerId);

    Optional<InterviewFeedback> findByInterviewIdAndInterviewerId(UUID interviewId, UUID interviewerId);

    @Query("""
            SELECT count(f) FROM InterviewFeedback f
            WHERE f.interview.application.id = :applicationId
            """)
    long countForApplication(@Param("applicationId") UUID applicationId);
}
