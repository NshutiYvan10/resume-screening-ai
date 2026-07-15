package com.resumeai.repository;

import com.resumeai.domain.Invitation;
import com.resumeai.domain.enums.InvitationStatus;
import com.resumeai.domain.enums.InvitationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    boolean existsByEmailIgnoreCaseAndStatus(String email, InvitationStatus status);

    Page<Invitation> findByType(InvitationType type, Pageable pageable);

    Page<Invitation> findByCompanyId(UUID companyId, Pageable pageable);
}
