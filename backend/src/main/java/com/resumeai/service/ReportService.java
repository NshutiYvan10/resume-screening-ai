package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Company;
import com.resumeai.domain.Job;
import com.resumeai.domain.Report;
import com.resumeai.domain.ReportApproval;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.NotificationType;
import com.resumeai.domain.enums.ReportApprovalAction;
import com.resumeai.domain.enums.ReportScope;
import com.resumeai.domain.enums.ReportStatus;
import com.resumeai.domain.enums.Role;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.ReportDtos.*;
import com.resumeai.repository.*;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Report lifecycle: authorise, queue, render asynchronously, then run the sign-off
 * workflow. Rendering never happens on the request thread, so a report covering a large
 * dataset cannot block the caller - the UI polls the status instead.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportApprovalRepository approvalRepository;
    private final ReportRenderWorker renderWorker;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final TransactionTemplate transactionTemplate;

    // ------------------------------------------------------------- catalogue

    public List<ReportTypeOption> availableTypes() {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        return ReportCatalog.forRole(actor.getRole()).stream().map(ReportTypeOption::from).toList();
    }

    // -------------------------------------------------------------- generate

    @Transactional
    public ReportSummary generate(GenerateReportRequest request) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        ReportCatalog.Definition def = ReportCatalog.require(request.type());
        ReportCatalog.requireRoleMay(actor.getRole(), request.type());

        Company company = null;
        User subject = null;
        Map<String, Object> parameters = new HashMap<>();

        if (def.scope() == ReportScope.COMPANY) {
            company = companyRepository.findById(requireCompanyId(actor))
                    .orElseThrow(() -> ApiException.notFound("Company not found"));
        } else if (def.scope() == ReportScope.PERSONAL) {
            subject = userRepository.findById(actor.getId())
                    .orElseThrow(() -> ApiException.notFound("User not found"));
            if (actor.getCompanyId() != null) {
                company = companyRepository.findById(actor.getCompanyId()).orElse(null);
            }
        }

        if (def.requiresJob()) {
            if (request.jobId() == null) {
                throw ApiException.badRequest("Select a job posting for this report");
            }
            Job job = jobRepository.findById(request.jobId())
                    .orElseThrow(() -> ApiException.notFound("Job posting not found"));
            // a job-scoped report must not become a way to read another tenant's pipeline
            if (!job.getCompany().getId().equals(requireCompanyId(actor))) {
                throw ApiException.forbidden("This job belongs to another company");
            }
            parameters.put("jobId", job.getId().toString());
            parameters.put("jobTitle", job.getTitle());
        }

        Report report = reportRepository.save(Report.builder()
                .referenceNo(nextReferenceNo())
                .type(def.type())
                .scope(def.scope())
                .status(ReportStatus.QUEUED)
                .title(def.title())
                .company(company)
                .subjectUser(subject)
                .periodStart(request.periodStart())
                .periodEnd(request.periodEnd())
                .parameters(parameters)
                .generatedBy(userRepository.getReferenceById(actor.getId()))
                .generatedByName(actor.getFullName())
                .generatedByRole(actor.getRole().name())
                .build());

        auditService.log("REPORT_REQUESTED", "REPORT", report.getId().toString(),
                Map.of("type", def.type().name(), "reference", report.getReferenceNo()));

        // render only once the row is durably visible to the worker thread
        UUID reportId = report.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                renderWorker.run(reportId);
            }
        });
        return ReportSummary.from(report);
    }

    // -------------------------------------------------------------- workflow

    @Transactional
    public ReportDetail submitForApproval(UUID reportId) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Report report = requireVisible(reportId, actor);
        if (!ReportCatalog.require(report.getType()).requiresApproval()) {
            throw ApiException.badRequest("This report is final on generation and needs no approval");
        }
        if (report.getStatus() != ReportStatus.DRAFT && report.getStatus() != ReportStatus.REJECTED) {
            throw ApiException.badRequest("Only a draft or rejected report can be sent for approval");
        }
        if (report.getGeneratedBy() == null || !report.getGeneratedBy().getId().equals(actor.getId())) {
            throw ApiException.forbidden("Only the person who generated a report can send it for approval");
        }

        report.setStatus(ReportStatus.PENDING_APPROVAL);
        trail(report, ReportApprovalAction.SUBMITTED_FOR_APPROVAL, actor.getFullName(),
                actor.getRole().name(), null);
        auditService.log("REPORT_SUBMITTED_FOR_APPROVAL", "REPORT", report.getId().toString(),
                Map.of("reference", report.getReferenceNo()));
        notifyApprovers(report, actor);
        return detail(report, actor);
    }

    @Transactional
    public ReportDetail approve(UUID reportId, String note) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Report report = requireDecidable(reportId, actor);

        Instant now = Instant.now();
        report.setStatus(ReportStatus.APPROVED);
        report.setApprovedBy(userRepository.getReferenceById(actor.getId()));
        report.setApprovedByName(actor.getFullName());
        report.setApprovedByRole(actor.getRole().name());
        report.setApprovedAt(now);
        trail(report, ReportApprovalAction.APPROVED, actor.getFullName(), actor.getRole().name(), note);
        auditService.log("REPORT_APPROVED", "REPORT", report.getId().toString(),
                Map.of("reference", report.getReferenceNo(), "approver", actor.getEmail()));

        // the approved document differs from the draft (no watermark, signed footer),
        // so it is re-rendered rather than re-labelled
        UUID id = report.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                renderWorker.run(id);
            }
        });
        notifyGenerator(report, actor, true, note);
        return detail(report, actor);
    }

    @Transactional
    public ReportDetail reject(UUID reportId, String note) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Report report = requireDecidable(reportId, actor);
        report.setStatus(ReportStatus.REJECTED);
        trail(report, ReportApprovalAction.REJECTED, actor.getFullName(), actor.getRole().name(), note);
        auditService.log("REPORT_REJECTED", "REPORT", report.getId().toString(),
                Map.of("reference", report.getReferenceNo(), "reviewer", actor.getEmail()));
        notifyGenerator(report, actor, false, note);
        return detail(report, actor);
    }

    // ----------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public PageResponse<ReportSummary> list(com.resumeai.domain.enums.ReportType type,
                                            ReportStatus status,
                                            Instant from, Instant to,
                                            int page, int size) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        UUID companyFilter = null;
        UUID ownerFilter = null;
        switch (actor.getRole()) {
            case SUPER_ADMIN -> { /* everything */ }
            case COMPANY_ADMIN -> companyFilter = requireCompanyId(actor);
            // recruiters and candidates only ever see documents they generated
            case RECRUITER, CANDIDATE -> ownerFilter = actor.getId();
        }
        Page<Report> result = reportRepository.search(companyFilter, ownerFilter, type, status, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result, ReportSummary::from);
    }

    @Transactional(readOnly = true)
    public ReportDetail get(UUID reportId) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        return detail(requireVisible(reportId, actor), actor);
    }

    /** Bytes of the rendered PDF, for inline preview or download. */
    @Transactional(readOnly = true)
    public Download download(UUID reportId) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Report report = requireVisible(reportId, actor);
        if (report.getFilePath() == null) {
            throw ApiException.badRequest(report.getStatus() == ReportStatus.FAILED
                    ? "This report failed to generate"
                    : "This report is still being generated");
        }
        try {
            byte[] bytes = Files.readAllBytes(fileStorageService.resolve(report.getFilePath()));
            return new Download(bytes, report.getReferenceNo() + ".pdf");
        } catch (Exception e) {
            throw ApiException.notFound("The report file is no longer available");
        }
    }

    public record Download(byte[] bytes, String fileName) {
    }

    // --------------------------------------------------------------- helpers

    /** Reports the caller is allowed to read: own, company-wide, or all. */
    private Report requireVisible(UUID reportId, UserPrincipal actor) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("Report not found"));
        boolean own = report.getGeneratedBy() != null && report.getGeneratedBy().getId().equals(actor.getId());
        boolean sameCompany = report.getCompany() != null
                && report.getCompany().getId().equals(actor.getCompanyId());
        boolean allowed = switch (actor.getRole()) {
            case SUPER_ADMIN -> true;
            case COMPANY_ADMIN -> own || sameCompany;
            case RECRUITER, CANDIDATE -> own;
        };
        if (!allowed) {
            throw ApiException.forbidden("You cannot access this report");
        }
        return report;
    }

    /**
     * Hierarchical sign-off: a recruiter's report is approved by a company admin, and a
     * company admin's by a different company admin (or a super admin when they are the
     * only administrator). Platform and personal reports have no superior and are
     * acknowledged by their author at generation time instead.
     */
    private Report requireDecidable(UUID reportId, UserPrincipal actor) {
        Report report = requireVisible(reportId, actor);
        if (report.getStatus() != ReportStatus.PENDING_APPROVAL) {
            throw ApiException.badRequest("This report is not awaiting approval");
        }
        Role generatorRole = Role.valueOf(report.getGeneratedByRole());
        boolean self = report.getGeneratedBy() != null
                && report.getGeneratedBy().getId().equals(actor.getId());

        switch (generatorRole) {
            case RECRUITER -> {
                if (actor.getRole() != Role.COMPANY_ADMIN
                        || !report.getCompany().getId().equals(actor.getCompanyId())) {
                    throw ApiException.forbidden("Only a company administrator of this company can approve it");
                }
            }
            case COMPANY_ADMIN -> {
                if (actor.getRole() == Role.SUPER_ADMIN) {
                    return report;
                }
                if (actor.getRole() != Role.COMPANY_ADMIN
                        || !report.getCompany().getId().equals(actor.getCompanyId())) {
                    throw ApiException.forbidden("Only a company administrator of this company can approve it");
                }
                if (self && otherAdminsExist(report.getCompany().getId(), actor.getId())) {
                    throw ApiException.forbidden(
                            "Another company administrator must approve your own report");
                }
            }
            // A platform administrator has no superior, so their reports are acknowledged
            // at generation and never enter the approval queue. Reaching here would mean
            // a stale row, so refuse rather than stage a self-approval.
            case SUPER_ADMIN, CANDIDATE ->
                    throw ApiException.badRequest("This report is final on generation and needs no approval");
        }
        return report;
    }

    private boolean otherAdminsExist(UUID companyId, UUID excludingUserId) {
        return userRepository.findByCompanyIdAndRoleIn(companyId, List.of(Role.COMPANY_ADMIN)).stream()
                .anyMatch(u -> !u.getId().equals(excludingUserId));
    }

    private void notifyApprovers(Report report, UserPrincipal requester) {
        List<User> approvers = report.getCompany() == null
                ? List.of()
                : userRepository.findByCompanyIdAndRoleIn(report.getCompany().getId(),
                        List.of(Role.COMPANY_ADMIN)).stream()
                        .filter(u -> !u.getId().equals(requester.getId()))
                        .toList();
        for (User approver : approvers) {
            notificationService.notify(approver, NotificationType.REPORT_APPROVAL_NEEDED,
                    "Report awaiting your approval",
                    requester.getFullName() + " submitted \"" + report.getTitle()
                            + "\" (" + report.getReferenceNo() + ") for approval.",
                    "/reports", false);
        }
    }

    private void notifyGenerator(Report report, UserPrincipal decider, boolean approved, String note) {
        if (report.getGeneratedBy() == null || report.getGeneratedBy().getId().equals(decider.getId())) {
            return;
        }
        notificationService.notify(report.getGeneratedBy(), NotificationType.REPORT_DECISION,
                approved ? "Report approved" : "Report returned",
                decider.getFullName() + (approved ? " approved " : " returned ") + report.getTitle()
                        + " (" + report.getReferenceNo() + ")"
                        + (note != null && !note.isBlank() ? ": " + note : ""),
                "/reports", false);
    }

    private void trail(Report report, ReportApprovalAction action, String name, String role, String note) {
        approvalRepository.save(ReportApproval.builder()
                .report(report)
                .action(action)
                .actor(report.getGeneratedBy())
                .actorName(name)
                .actorRole(role)
                .note(note)
                .build());
    }

    private ReportDetail detail(Report report, UserPrincipal actor) {
        ReportCatalog.Definition def = ReportCatalog.require(report.getType());
        boolean own = report.getGeneratedBy() != null
                && report.getGeneratedBy().getId().equals(actor.getId());
        boolean canSubmit = def.requiresApproval() && own
                && (report.getStatus() == ReportStatus.DRAFT || report.getStatus() == ReportStatus.REJECTED);
        boolean canDecide = report.getStatus() == ReportStatus.PENDING_APPROVAL && canDecideQuietly(report, actor);
        return new ReportDetail(ReportSummary.from(report), def.description(),
                report.getPeriodStart(), report.getPeriodEnd(), report.getParameters(),
                report.getChecksum(),
                approvalRepository.findByReportIdOrderByCreatedAtAsc(report.getId()).stream()
                        .map(ApprovalEntry::from).toList(),
                canSubmit, canDecide);
    }

    /** Same rule as requireDecidable, expressed as a predicate for the UI. */
    private boolean canDecideQuietly(Report report, UserPrincipal actor) {
        try {
            Role generatorRole = Role.valueOf(report.getGeneratedByRole());
            boolean self = report.getGeneratedBy() != null
                    && report.getGeneratedBy().getId().equals(actor.getId());
            return switch (generatorRole) {
                case RECRUITER -> actor.getRole() == Role.COMPANY_ADMIN
                        && report.getCompany() != null
                        && report.getCompany().getId().equals(actor.getCompanyId());
                case COMPANY_ADMIN -> actor.getRole() == Role.SUPER_ADMIN
                        || (actor.getRole() == Role.COMPANY_ADMIN
                            && report.getCompany() != null
                            && report.getCompany().getId().equals(actor.getCompanyId())
                            && !(self && otherAdminsExist(report.getCompany().getId(), actor.getId())));
                case SUPER_ADMIN -> actor.getRole() == Role.SUPER_ADMIN;
                case CANDIDATE -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    private UUID requireCompanyId(UserPrincipal actor) {
        if (actor.getCompanyId() == null) {
            throw ApiException.forbidden("You are not associated with a company");
        }
        return actor.getCompanyId();
    }

    /** RPT-<year>-<sequence>, unique and readable enough to quote in an email. */
    private String nextReferenceNo() {
        Instant yearStart = LocalDate.now(ZoneOffset.UTC).withDayOfYear(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        long seq = reportRepository.countByCreatedAtAfter(yearStart) + 1;
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = String.format("RPT-%d-%06d", year, seq + attempt);
            if (!reportRepository.existsByReferenceNo(candidate)) {
                return candidate;
            }
        }
        return "RPT-" + year + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

}
