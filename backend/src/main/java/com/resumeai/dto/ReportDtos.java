package com.resumeai.dto;

import com.resumeai.domain.Report;
import com.resumeai.domain.ReportApproval;
import com.resumeai.domain.enums.ReportApprovalAction;
import com.resumeai.domain.enums.ReportScope;
import com.resumeai.domain.enums.ReportStatus;
import com.resumeai.domain.enums.ReportType;
import com.resumeai.service.ReportCatalog;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReportDtos {

    /** A report type the caller is allowed to generate. */
    public record ReportTypeOption(ReportType type,
                                   String title,
                                   String description,
                                   ReportScope scope,
                                   boolean requiresApproval,
                                   boolean requiresJob) {

        public static ReportTypeOption from(ReportCatalog.Definition d) {
            return new ReportTypeOption(d.type(), d.title(), d.description(), d.scope(),
                    d.requiresApproval(), d.requiresJob());
        }
    }

    public record GenerateReportRequest(@NotNull ReportType type,
                                        /* required only for job-scoped types */
                                        UUID jobId,
                                        LocalDate periodStart,
                                        LocalDate periodEnd) {
    }

    public record DecisionRequest(@Size(max = 1000) String note) {
    }

    public record ApprovalEntry(ReportApprovalAction action,
                                String actorName,
                                String actorPhotoUrl,
                                String actorRole,
                                String note,
                                Instant at) {

        public static ApprovalEntry from(ReportApproval a) {
            return new ApprovalEntry(a.getAction(), a.getActorName(),
                    a.getActor() != null
                            ? ProfileDtos.photoUrl(a.getActor().getId(), a.getActor().getPhotoPath())
                            : null,
                    a.getActorRole(),
                    a.getNote(), a.getCreatedAt());
        }
    }

    /** Archive row. Deliberately free of lazy associations beyond the ids it needs. */
    public record ReportSummary(UUID id,
                                String referenceNo,
                                ReportType type,
                                String title,
                                ReportScope scope,
                                ReportStatus status,
                                String companyName,
                                String generatedByName,
                                String generatedByPhotoUrl,
                                String generatedByRole,
                                Instant generatedAt,
                                String approvedByName,
                                String approvedByPhotoUrl,
                                Instant approvedAt,
                                Integer pageCount,
                                Long fileSizeBytes,
                                boolean requiresApproval,
                                String failureReason,
                                Instant createdAt) {

        public static ReportSummary from(Report r) {
            return new ReportSummary(r.getId(), r.getReferenceNo(), r.getType(), r.getTitle(),
                    r.getScope(), r.getStatus(),
                    r.getCompany() != null ? r.getCompany().getName() : null,
                    r.getGeneratedByName(),
                    r.getGeneratedBy() != null
                            ? ProfileDtos.photoUrl(r.getGeneratedBy().getId(), r.getGeneratedBy().getPhotoPath())
                            : null,
                    r.getGeneratedByRole(), r.getGeneratedAt(),
                    r.getApprovedByName(),
                    r.getApprovedBy() != null
                            ? ProfileDtos.photoUrl(r.getApprovedBy().getId(), r.getApprovedBy().getPhotoPath())
                            : null,
                    r.getApprovedAt(),
                    r.getPageCount(), r.getFileSizeBytes(),
                    ReportCatalog.require(r.getType()).requiresApproval(),
                    r.getFailureReason(), r.getCreatedAt());
        }
    }

    /** Full detail view: summary plus provenance and the approval trail. */
    public record ReportDetail(ReportSummary summary,
                               String description,
                               LocalDate periodStart,
                               LocalDate periodEnd,
                               Map<String, Object> parameters,
                               String checksum,
                               List<ApprovalEntry> trail,
                               boolean canSubmitForApproval,
                               boolean canDecide) {
    }
}
