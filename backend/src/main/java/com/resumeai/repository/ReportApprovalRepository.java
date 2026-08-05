package com.resumeai.repository;

import com.resumeai.domain.ReportApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportApprovalRepository extends JpaRepository<ReportApproval, Long> {

    List<ReportApproval> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
