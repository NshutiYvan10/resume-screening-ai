package com.resumeai.repository;

import com.resumeai.domain.Job;
import com.resumeai.domain.enums.EmploymentType;
import com.resumeai.domain.enums.JobStatus;
import com.resumeai.domain.enums.WorkMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    @Query("""
            SELECT j FROM Job j
            WHERE j.company.id = :companyId
              AND (:status IS NULL OR j.status = :status)
              AND (:search IS NULL OR lower(j.title) LIKE lower(concat('%', cast(:search as string), '%')))
            """)
    Page<Job> searchCompanyJobs(@Param("companyId") UUID companyId,
                                @Param("status") JobStatus status,
                                @Param("search") String search,
                                Pageable pageable);

    @Query("""
            SELECT j FROM Job j JOIN j.company c
            WHERE j.status = com.resumeai.domain.enums.JobStatus.PUBLISHED
              AND c.status = com.resumeai.domain.enums.CompanyStatus.ACTIVE
              AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
              AND (:search IS NULL OR lower(j.title) LIKE lower(concat('%', cast(:search as string), '%'))
                   OR lower(c.name) LIKE lower(concat('%', cast(:search as string), '%')))
              AND (:location IS NULL OR lower(j.location) LIKE lower(concat('%', cast(:location as string), '%')))
              AND (:employmentType IS NULL OR j.employmentType = :employmentType)
              AND (:workMode IS NULL OR j.workMode = :workMode)
              AND (:companyId IS NULL OR c.id = :companyId)
            """)
    Page<Job> searchPublicJobs(@Param("search") String search,
                               @Param("location") String location,
                               @Param("employmentType") EmploymentType employmentType,
                               @Param("workMode") WorkMode workMode,
                               @Param("companyId") UUID companyId,
                               Pageable pageable);

    long countByCompanyId(UUID companyId);

    long countByCompanyIdAndStatus(UUID companyId, JobStatus status);

    /** Jobs a candidate can actually see & apply to — matches searchPublicJobs' filter. */
    @Query("""
            SELECT count(j) FROM Job j
            WHERE j.company.id = :companyId
              AND j.status = com.resumeai.domain.enums.JobStatus.PUBLISHED
              AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
            """)
    long countOpenPublicJobs(@Param("companyId") UUID companyId);

    long countByStatus(JobStatus status);
}
