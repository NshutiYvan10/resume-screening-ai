package com.resumeai.repository;

import com.resumeai.domain.CoverLetterTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoverLetterTemplateRepository extends JpaRepository<CoverLetterTemplate, UUID> {

    List<CoverLetterTemplate> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<CoverLetterTemplate> findByUserIdAndIsDefaultTrue(UUID userId);

    long countByUserId(UUID userId);

    /** Clear the current default first; a partial unique index allows only one. */
    @Modifying
    @Query("""
            UPDATE CoverLetterTemplate t SET t.isDefault = false
            WHERE t.user.id = :userId AND t.isDefault = true
            """)
    void clearDefault(@Param("userId") UUID userId);
}
