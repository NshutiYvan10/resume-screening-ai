package com.resumeai.repository;

import com.resumeai.domain.CompanyPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyPhotoRepository extends JpaRepository<CompanyPhoto, UUID> {

    List<CompanyPhoto> findByCompanyIdOrderBySortOrderAscCreatedAtAsc(UUID companyId);

    long countByCompanyId(UUID companyId);
}
