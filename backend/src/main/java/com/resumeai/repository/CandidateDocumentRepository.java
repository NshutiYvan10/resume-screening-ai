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
}
