package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Application;
import com.resumeai.domain.AuditLog;
import com.resumeai.domain.Job;
import com.resumeai.domain.Report;
import com.resumeai.domain.ScreeningResult;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.Role;
import com.resumeai.repository.ApplicationRepository;
import com.resumeai.repository.AuditLogRepository;
import com.resumeai.repository.JobRepository;
import com.resumeai.repository.ScreeningResultRepository;
import com.resumeai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the data model for each report type straight from the database at generation
 * time, so a report never reflects a stale UI snapshot.
 *
 * <p>Every method takes explicit ids rather than reading the SecurityContext: rendering
 * happens on a background thread where no authentication is bound. Authorisation has
 * already been done by {@link ReportService} before the job is queued.
 */
@Service
@RequiredArgsConstructor
public class ReportDataService {

    /** Cap on rows pulled into a single document, so one report cannot exhaust memory. */
    private static final int MAX_ROWS = 500;

    private final AnalyticsService analyticsService;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationRepository applicationRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> build(Report report) {
        UUID companyId = report.getCompany() != null ? report.getCompany().getId() : null;
        UUID subjectId = report.getSubjectUser() != null ? report.getSubjectUser().getId() : null;
        Map<String, Object> parameters = report.getParameters() != null ? report.getParameters() : Map.of();

        return switch (report.getType()) {
            case PLATFORM_ACTIVITY_SUMMARY -> analyticsService.platform();
            case PLATFORM_AUDIT_TRAIL -> auditSection(null);
            case AI_SCREENING_HEALTH -> screeningHealth();
            case COMPANY_HIRING_PERFORMANCE -> analyticsService.companyFor(requireCompany(companyId));
            case TEAM_PERFORMANCE -> teamPerformance(requireCompany(companyId));
            case JOB_POSTING_PERFORMANCE -> jobPostingPerformance(requireCompany(companyId));
            case COMPANY_AUDIT_TRAIL -> auditSection(requireCompany(companyId));
            case RECRUITER_ACTIVITY -> analyticsService.recruiterFor(requireSubject(subjectId));
            case CANDIDATE_SHORTLIST -> shortlist(parameters);
            case CANDIDATE_APPLICATION_HISTORY -> candidateHistory(requireSubject(subjectId));
        };
    }

    // ------------------------------------------------------------- platform

    private Map<String, Object> screeningHealth() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : screeningResultRepository.countGroupByStatus()) {
            byStatus.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        out.put("screeningByStatus", byStatus);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long failed = byStatus.getOrDefault("FAILED", 0L);
        out.put("totalScreenings", total);
        out.put("failureRatePct", total == 0 ? null : Math.round(failed * 1000.0 / total) / 10.0);

        // fairness / identity advisories, counted over the whole platform
        List<ScreeningResult> recent = screeningResultRepository
                .findAll(PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        long bias = recent.stream().filter(ScreeningResult::isBiasFlag).count();
        long identity = recent.stream().filter(r -> !r.isIdentityVerified()).count();
        out.put("sampleSize", recent.size());
        out.put("biasFlagged", bias);
        out.put("identityFlagged", identity);

        Map<String, Long> parseQuality = new LinkedHashMap<>();
        for (ScreeningResult r : recent) {
            String q = r.getParseQuality() != null ? r.getParseQuality() : "UNKNOWN";
            parseQuality.merge(q, 1L, Long::sum);
        }
        out.put("parseQuality", parseQuality);
        return out;
    }

    // -------------------------------------------------------------- company

    private Map<String, Object> teamPerformance(UUID companyId) {
        List<User> team = userRepository.findByCompanyIdAndRoleIn(companyId,
                List.of(Role.COMPANY_ADMIN, Role.RECRUITER));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User member : team) {
            Map<String, Object> stats = analyticsService.recruiterFor(member.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", member.getFullName());
            row.put("email", member.getEmail());
            row.put("role", member.getRole().name());
            row.put("totalJobs", stats.get("totalJobs"));
            row.put("publishedJobs", stats.get("publishedJobs"));
            row.put("totalApplications", stats.get("totalApplications"));
            row.put("averageMatchScore", stats.get("averageMatchScore"));
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare(asLong(b.get("totalApplications")), asLong(a.get("totalApplications"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("teamSize", rows.size());
        out.put("members", rows);
        return out;
    }

    private Map<String, Object> jobPostingPerformance(UUID companyId) {
        Map<String, Object> company = analyticsService.companyFor(companyId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalJobs", company.get("totalJobs"));
        out.put("publishedJobs", company.get("publishedJobs"));
        out.put("draftJobs", company.get("draftJobs"));
        out.put("closedJobs", company.get("closedJobs"));
        out.put("pendingApprovalJobs", company.get("pendingApprovalJobs"));
        out.put("jobPerformance", company.get("jobPerformance"));
        return out;
    }

    private Map<String, Object> auditSection(UUID companyId) {
        List<AuditLog> logs = auditLogRepository
                .search(companyId, null, null, null, null,
                        PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        List<Map<String, Object>> rows = logs.stream().map(l -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("at", l.getCreatedAt());
            row.put("actor", l.getActorEmail() != null ? l.getActorEmail() : "system");
            row.put("role", l.getActorRole() != null ? l.getActorRole() : "SYSTEM");
            row.put("action", l.getAction());
            row.put("entityType", l.getEntityType());
            row.put("entityId", l.getEntityId());
            row.put("ip", l.getIpAddress());
            return row;
        }).toList();

        Map<String, Long> byAction = new LinkedHashMap<>();
        logs.forEach(l -> byAction.merge(l.getAction(), 1L, Long::sum));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entryCount", rows.size());
        out.put("truncated", rows.size() >= MAX_ROWS);
        out.put("byAction", byAction);
        out.put("entries", rows);
        return out;
    }

    // ------------------------------------------------------------ recruiter

    private Map<String, Object> shortlist(Map<String, Object> parameters) {
        Object raw = parameters.get("jobId");
        if (raw == null) {
            throw ApiException.badRequest("This report needs a job posting to be selected");
        }
        UUID jobId;
        try {
            jobId = UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid job selection");
        }
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("Job posting not found"));

        List<Application> apps = applicationRepository
                .searchJobApplications(jobId, null, null,
                        PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "appliedAt")))
                .getContent();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Application a : apps) {
            ScreeningResult sr = a.getScreeningResult();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("candidate", a.getCandidate().getFullName());
            row.put("email", a.getCandidate().getEmail());
            row.put("status", a.getStatus().name());
            row.put("appliedAt", a.getAppliedAt());
            row.put("matchScore", sr != null ? sr.getMatchScore() : null);
            row.put("skillsScore", sr != null ? sr.getSkillsScore() : null);
            row.put("experienceYears", sr != null ? sr.getExtractedExperienceYears() : null);
            row.put("education", sr != null ? sr.getExtractedEducation() : null);
            row.put("biasFlag", sr != null && sr.isBiasFlag());
            rows.add(row);
        }
        // highest match score first; unscored applications sink to the bottom
        rows.sort((a, b) -> compareScoreDesc((BigDecimal) a.get("matchScore"), (BigDecimal) b.get("matchScore")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jobTitle", job.getTitle());
        out.put("jobStatus", job.getStatus().name());
        out.put("jobLocation", job.getLocation());
        out.put("jobDeadline", job.getDeadline());
        out.put("candidateCount", rows.size());
        out.put("candidates", rows);
        return out;
    }

    // ------------------------------------------------------------ candidate

    private Map<String, Object> candidateHistory(UUID candidateId) {
        Map<String, Object> out = new LinkedHashMap<>(analyticsService.candidateFor(candidateId));
        List<Application> apps = applicationRepository
                .findByCandidateId(candidateId,
                        PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "appliedAt")))
                .getContent();
        out.put("applications", apps.stream().map(a -> {
            ScreeningResult sr = a.getScreeningResult();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jobTitle", a.getJob().getTitle());
            row.put("company", a.getJob().getCompany().getName());
            row.put("status", a.getStatus().name());
            row.put("appliedAt", a.getAppliedAt());
            row.put("matchScore", sr != null ? sr.getMatchScore() : null);
            return row;
        }).toList());
        return out;
    }

    // -------------------------------------------------------------- helpers

    private static int compareScoreDesc(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }

    private static long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private UUID requireCompany(UUID companyId) {
        if (companyId == null) {
            throw ApiException.badRequest("This report requires a company");
        }
        return companyId;
    }

    private UUID requireSubject(UUID subjectId) {
        if (subjectId == null) {
            throw ApiException.badRequest("This report requires a subject user");
        }
        return subjectId;
    }
}
