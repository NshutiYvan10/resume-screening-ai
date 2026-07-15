package com.resumeai.repository;

import com.resumeai.domain.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    @Query("""
            SELECT c FROM Company c
            WHERE (:search IS NULL OR lower(c.name) LIKE lower(concat('%', cast(:search as string), '%'))
                   OR lower(c.industry) LIKE lower(concat('%', cast(:search as string), '%')))
            """)
    Page<Company> search(@Param("search") String search, Pageable pageable);
}
