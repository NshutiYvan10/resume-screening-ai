package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.enums.ApplicationStatus;
import com.resumeai.domain.enums.JobStatus;
import com.resumeai.domain.enums.Role;
import com.resumeai.repository.*;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final ScreeningResultRepository screeningResultRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> platform() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalCompanies", companyRepository.count());
        out.put("totalUsers", userRepository.count());
        out.put("totalCandidates", userRepository.countByRole(Role.CANDIDATE));
        out.put("totalRecruiters",
                userRepository.countByRole(Role.RECRUITER) + userRepository.countByRole(Role.COMPANY_ADMIN));
        out.put("totalJobs", jobRepository.count());
        out.put("publishedJobs", jobRepository.countByStatus(JobStatus.PUBLISHED));
        out.put("totalApplications", applicationRepository.count());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> company() {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        UUID companyId = actor.getCompanyId();
        if (companyId == null) {
            throw ApiException.forbidden("You are not associated with a company");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalJobs", jobRepository.countByCompanyId(companyId));
        out.put("publishedJobs", jobRepository.countByCompanyIdAndStatus(companyId, JobStatus.PUBLISHED));
        out.put("draftJobs", jobRepository.countByCompanyIdAndStatus(companyId, JobStatus.DRAFT));
        out.put("closedJobs", jobRepository.countByCompanyIdAndStatus(companyId, JobStatus.CLOSED));
        out.put("totalApplications", applicationRepository.countByCompany(companyId));
        out.put("teamMembers", userRepository.countByCompanyId(companyId));

        BigDecimal avg = applicationRepository.averageScoreForCompany(companyId);
        out.put("averageMatchScore", avg != null ? avg.setScale(1, RoundingMode.HALF_UP) : null);

        // pipeline funnel
        Map<String, Long> pipeline = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            pipeline.put(status.name(), 0L);
        }
        applicationRepository.countByCompanyGroupByStatus(companyId)
                .forEach(sc -> pipeline.put(sc.getStatus().name(), sc.getCnt()));
        out.put("pipeline", pipeline);

        // AI score distribution in 10-point buckets
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            distribution.put(i * 10 + "-" + (i * 10 + 10), 0L);
        }
        for (Object[] row : screeningResultRepository.scoreDistributionForCompany(companyId)) {
            int bucket = ((Number) row[0]).intValue(); // 1..10
            long count = ((Number) row[1]).longValue();
            if (bucket >= 1 && bucket <= 10) {
                distribution.put((bucket - 1) * 10 + "-" + bucket * 10, count);
            }
        }
        out.put("scoreDistribution", distribution);

        // most common candidate skills
        List<Map<String, Object>> topSkills = screeningResultRepository
                .topSkillsForCompany(companyId, 12).stream()
                .map(row -> Map.<String, Object>of("skill", row[0], "count", ((Number) row[1]).longValue()))
                .toList();
        out.put("topSkills", topSkills);

        return out;
    }
}
