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
    private final OfferRepository offerRepository;

    // ============================================================ platform

    @Transactional(readOnly = true)
    public Map<String, Object> platform() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalCompanies", companyRepository.count());
        out.put("activeCompanies", jobRepository.countActiveCompanies());
        out.put("totalUsers", userRepository.count());
        out.put("totalCandidates", userRepository.countByRole(Role.CANDIDATE));
        out.put("totalRecruiters",
                userRepository.countByRole(Role.RECRUITER) + userRepository.countByRole(Role.COMPANY_ADMIN));
        out.put("totalJobs", jobRepository.count());
        out.put("publishedJobs", jobRepository.countByStatus(JobStatus.PUBLISHED));
        out.put("totalApplications", applicationRepository.count());

        // users by role
        Map<String, Long> usersByRole = new LinkedHashMap<>();
        for (Role r : Role.values()) {
            usersByRole.put(r.name(), userRepository.countByRole(r));
        }
        out.put("usersByRole", usersByRole);

        // jobs by status (all statuses, zero-filled)
        Map<String, Long> jobsByStatus = new LinkedHashMap<>();
        for (JobStatus s : JobStatus.values()) {
            jobsByStatus.put(s.name(), 0L);
        }
        for (Object[] row : jobRepository.countGroupByStatus()) {
            jobsByStatus.put(((JobStatus) row[0]).name(), ((Number) row[1]).longValue());
        }
        out.put("jobsByStatus", jobsByStatus);

        // platform-wide application status distribution
        out.put("applicationsByStatus", zeroFilledStatusMap(applicationRepository.countGroupByStatus()));

        // AI screening health (rows grouped by status)
        Map<String, Long> screeningHealth = new LinkedHashMap<>();
        for (Object[] row : screeningResultRepository.countGroupByStatus()) {
            screeningHealth.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        out.put("screeningHealth", screeningHealth);

        // growth over time
        out.put("applicationsOverTime", monthSeries(applicationRepository.applicationsByMonth()));
        out.put("usersOverTime", monthSeries(userRepository.newUsersByMonth()));
        return out;
    }

    // ============================================================= company

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
        out.put("pendingApprovalJobs",
                jobRepository.countByCompanyIdAndStatus(companyId, JobStatus.PENDING_APPROVAL));
        out.put("totalApplications", applicationRepository.countByCompany(companyId));
        out.put("teamMembers", userRepository.countByCompanyId(companyId));

        BigDecimal avg = applicationRepository.averageScoreForCompany(companyId);
        out.put("averageMatchScore", avg != null ? avg.setScale(1, RoundingMode.HALF_UP) : null);

        Map<String, Long> pipeline = zeroFilledStatusMap(
                applicationRepository.countByCompanyGroupByStatus(companyId));
        out.put("pipeline", pipeline);
        out.put("hires", pipeline.getOrDefault(ApplicationStatus.HIRED.name(), 0L));

        out.put("scoreDistribution",
                bucketDistribution(screeningResultRepository.scoreDistributionForCompany(companyId)));

        List<Map<String, Object>> topSkills = screeningResultRepository
                .topSkillsForCompany(companyId, 12).stream()
                .map(row -> Map.<String, Object>of("skill", row[0], "count", ((Number) row[1]).longValue()))
                .toList();
        out.put("topSkills", topSkills);

        // time-to-hire (avg days)
        Double tth = applicationRepository.averageTimeToHireDays(companyId);
        out.put("avgTimeToHireDays", tth != null ? Math.round(tth * 10.0) / 10.0 : null);

        // offer outcomes + acceptance rate
        out.put("offers", offerStats(offerRepository.countByCompanyGroupByStatus(companyId)));

        // bias-flag rate
        out.put("biasFlaggedCount", screeningResultRepository.countBiasFlaggedForCompany(companyId));

        // applications over time + per-job performance
        out.put("applicationsOverTime", monthSeries(applicationRepository.applicationsByMonthForCompany(companyId)));
        out.put("jobPerformance", jobPerformance(jobRepository.jobPerformanceForCompany(companyId)));
        return out;
    }

    // ============================================================ recruiter

    @Transactional(readOnly = true)
    public Map<String, Object> recruiter() {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        UUID userId = actor.getId();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalJobs", jobRepository.countByCreatedById(userId));
        out.put("publishedJobs", jobRepository.countByCreatedByIdAndStatus(userId, JobStatus.PUBLISHED));
        out.put("draftJobs", jobRepository.countByCreatedByIdAndStatus(userId, JobStatus.DRAFT));
        out.put("pendingApprovalJobs",
                jobRepository.countByCreatedByIdAndStatus(userId, JobStatus.PENDING_APPROVAL));
        out.put("totalApplications", applicationRepository.countByRecruiter(userId));

        BigDecimal avg = applicationRepository.averageScoreForRecruiter(userId);
        out.put("averageMatchScore", avg != null ? avg.setScale(1, RoundingMode.HALF_UP) : null);

        out.put("pipeline", zeroFilledStatusMap(applicationRepository.countByRecruiterGroupByStatus(userId)));
        out.put("scoreDistribution",
                bucketDistribution(screeningResultRepository.scoreDistributionForRecruiter(userId)));
        out.put("jobPerformance", jobPerformance(jobRepository.jobPerformanceForRecruiter(userId)));
        return out;
    }

    // ============================================================ candidate

    @Transactional(readOnly = true)
    public Map<String, Object> candidate() {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        UUID candidateId = actor.getId();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalApplications", applicationRepository.countByCandidateId(candidateId));

        Map<String, Long> status = zeroFilledStatusMap(
                applicationRepository.countByCandidateGroupByStatus(candidateId));
        out.put("statusBreakdown", status);

        // headline funnel numbers derived from the status map
        long interviews = status.getOrDefault(ApplicationStatus.INTERVIEW.name(), 0L);
        long offers = status.getOrDefault(ApplicationStatus.OFFERED.name(), 0L)
                + status.getOrDefault(ApplicationStatus.HIRED.name(), 0L);
        out.put("interviews", interviews);
        out.put("offers", offers);
        out.put("hired", status.getOrDefault(ApplicationStatus.HIRED.name(), 0L));
        out.put("rejected", status.getOrDefault(ApplicationStatus.REJECTED.name(), 0L));

        BigDecimal avg = applicationRepository.averageScoreForCandidate(candidateId);
        out.put("averageMatchScore", avg != null ? avg.setScale(1, RoundingMode.HALF_UP) : null);

        out.put("scoreDistribution",
                bucketDistribution(screeningResultRepository.scoreDistributionForCandidate(candidateId)));
        out.put("applicationsOverTime",
                monthSeries(applicationRepository.applicationsByMonthForCandidate(candidateId)));
        return out;
    }

    // ============================================================= helpers

    private Map<String, Long> zeroFilledStatusMap(List<ApplicationRepository.StatusCount> counts) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            map.put(s.name(), 0L);
        }
        counts.forEach(sc -> map.put(sc.getStatus().name(), sc.getCnt()));
        return map;
    }

    /** 10 fixed 10-point buckets "0-10".."90-100" from a width_bucket(1..10) result. */
    private Map<String, Long> bucketDistribution(List<Object[]> rows) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            distribution.put(i * 10 + "-" + (i * 10 + 10), 0L);
        }
        for (Object[] row : rows) {
            int bucket = ((Number) row[0]).intValue(); // 1..10
            long count = ((Number) row[1]).longValue();
            if (bucket >= 1 && bucket <= 10) {
                distribution.put((bucket - 1) * 10 + "-" + bucket * 10, count);
            }
        }
        return distribution;
    }

    private List<Map<String, Object>> monthSeries(List<Object[]> rows) {
        return rows.stream()
                .map(row -> Map.<String, Object>of(
                        "month", row[0] != null ? row[0].toString() : "",
                        "count", ((Number) row[1]).longValue()))
                .toList();
    }

    private Map<String, Object> offerStats(List<Object[]> rows) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long accepted = 0;
        long declined = 0;
        for (Object[] row : rows) {
            String st = row[0].toString();
            long c = ((Number) row[1]).longValue();
            byStatus.put(st, c);
            if ("ACCEPTED".equals(st)) {
                accepted = c;
            } else if ("DECLINED".equals(st)) {
                declined = c;
            }
        }
        long resolved = accepted + declined;
        Double acceptanceRate = resolved > 0
                ? Math.round((accepted * 1000.0) / resolved) / 10.0 : null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("byStatus", byStatus);
        out.put("accepted", accepted);
        out.put("declined", declined);
        out.put("acceptanceRate", acceptanceRate);
        return out;
    }

    private List<Map<String, Object>> jobPerformance(List<Object[]> rows) {
        return rows.stream()
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("title", row[0]);
                    m.put("status", row[1] != null ? row[1].toString() : null);
                    m.put("applications", ((Number) row[2]).longValue());
                    Object avg = row[3];
                    m.put("avgScore", avg != null
                            ? Math.round(((Number) avg).doubleValue() * 10.0) / 10.0 : null);
                    return m;
                })
                .toList();
    }
}
