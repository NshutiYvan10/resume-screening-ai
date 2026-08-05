package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Company;
import com.resumeai.domain.Report;
import com.resumeai.domain.ReportApproval;
import com.resumeai.domain.enums.ReportApprovalAction;
import com.resumeai.domain.enums.ReportScope;
import com.resumeai.domain.enums.ReportStatus;
import com.resumeai.repository.ReportApprovalRepository;
import com.resumeai.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Renders report PDFs off the request thread.
 *
 * <p>This lives in its own bean deliberately: {@code @Async} is applied by a Spring
 * proxy, so a self-invocation from inside {@code ReportService} would silently run
 * synchronously and block the caller for the whole render.
 *
 * <p>Phased like {@code ScreeningService}: short transactions around the database work
 * with the expensive rendering in between, and no entity carried across phases.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportRenderWorker {

    private final ReportRepository reportRepository;
    private final ReportApprovalRepository approvalRepository;
    private final ReportDataService reportDataService;
    private final ReportPdfRenderer renderer;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;

    @Async
    public void run(UUID reportId) {
        try {
            render(reportId);
        } catch (Exception e) {
            log.error("Report generation failed for {}", reportId, e);
            transactionTemplate.executeWithoutResult(tx -> reportRepository.findById(reportId).ifPresent(r -> {
                r.setStatus(ReportStatus.FAILED);
                r.setFailureReason(truncate(rootMessage(e)));
            }));
        }
    }

    private void render(UUID reportId) {
        // phase 1: claim the job and snapshot everything the renderer needs
        Snapshot snap = transactionTemplate.execute(tx -> {
            Report r = reportRepository.findById(reportId)
                    .orElseThrow(() -> ApiException.notFound("Report not found"));
            r.setStatus(ReportStatus.GENERATING);
            ReportCatalog.Definition def = ReportCatalog.require(r.getType());
            Company c = r.getCompany();
            boolean reRender = r.getStatus() == ReportStatus.APPROVED || r.getApprovedAt() != null;
            return new Snapshot(def,
                    c != null ? c.getName() : null,
                    c != null ? c.getLogoPath() : null,
                    r.getReferenceNo(), r.getTitle(),
                    r.getGeneratedByName(), r.getGeneratedByRole(),
                    r.getApprovedByName(), r.getApprovedByRole(), r.getApprovedAt(),
                    r.getPeriodStart(), r.getPeriodEnd(), reRender);
        });

        // phase 2: read live data and render, outside the claiming transaction
        Map<String, Object> data = transactionTemplate.execute(tx ->
                reportDataService.build(reportRepository.findById(reportId)
                        .orElseThrow(() -> ApiException.notFound("Report not found"))));

        boolean autoFinal = !snap.def().requiresApproval();
        boolean approved = snap.approvedAt() != null;
        Instant generatedAt = Instant.now();

        ReportPdfRenderer.Meta meta = new ReportPdfRenderer.Meta(
                snap.referenceNo(), snap.title(), snap.def().description(),
                humanScope(snap.def().scope()),
                approved || autoFinal ? "Final" : "Draft - pending approval",
                !(approved || autoFinal),
                snap.generatedByName(), humanRole(snap.generatedByRole()), generatedAt,
                approved ? snap.approvedByName() : (autoFinal ? snap.generatedByName() : null),
                approved ? humanRole(snap.approvedByRole()) : (autoFinal ? humanRole(snap.generatedByRole()) : null),
                approved ? snap.approvedAt() : (autoFinal ? generatedAt : null),
                snap.companyName(), logoDataUri(snap.companyLogoPath()),
                snap.periodStart(), snap.periodEnd());

        ReportPdfRenderer.Rendered pdf = renderer.render(meta, snap.def().fragment(), data);
        String path = fileStorageService.storeReport(pdf.bytes(), reportId);
        String checksum = sha256(pdf.bytes());

        // phase 3: publish the artefact
        transactionTemplate.executeWithoutResult(tx -> {
            Report r = reportRepository.findById(reportId)
                    .orElseThrow(() -> ApiException.notFound("Report not found"));
            r.setFilePath(path);
            r.setFileSizeBytes((long) pdf.bytes().length);
            r.setPageCount(pdf.pageCount());
            r.setChecksum(checksum);
            r.setGeneratedAt(generatedAt);
            r.setFailureReason(null);
            if (approved) {
                // re-render triggered by approval: the document is already signed off
                r.setStatus(ReportStatus.APPROVED);
            } else if (autoFinal) {
                // Platform and personal reports have no superior to counter-sign, so the
                // author's own acknowledgement stands as the sign-off and the document
                // still names a signer.
                r.setStatus(ReportStatus.APPROVED);
                r.setApprovedBy(r.getGeneratedBy());
                r.setApprovedByName(r.getGeneratedByName());
                r.setApprovedByRole(r.getGeneratedByRole());
                r.setApprovedAt(generatedAt);
            } else {
                r.setStatus(ReportStatus.DRAFT);
            }
            if (!snap.reRender()) {
                approvalRepository.save(ReportApproval.builder()
                        .report(r)
                        .action(ReportApprovalAction.GENERATED)
                        .actor(r.getGeneratedBy())
                        .actorName(r.getGeneratedByName())
                        .actorRole(r.getGeneratedByRole())
                        .build());
                if (autoFinal) {
                    // make the self-sign-off explicit in the trail rather than only
                    // implying it through the approved_by columns
                    approvalRepository.save(ReportApproval.builder()
                            .report(r)
                            .action(ReportApprovalAction.ACKNOWLEDGED)
                            .actor(r.getGeneratedBy())
                            .actorName(r.getGeneratedByName())
                            .actorRole(r.getGeneratedByRole())
                            .note("Acknowledged by its author; no counter-signature required")
                            .build());
                }
            }
        });
    }

    // --------------------------------------------------------------- helpers

    private String logoDataUri(String logoPath) {
        if (logoPath == null || logoPath.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(fileStorageService.resolve(logoPath));
            String lower = logoPath.toLowerCase();
            String mime = lower.endsWith(".png") ? "image/png"
                    : lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            // branding is cosmetic: a missing logo must never fail a report
            log.warn("Could not inline company logo {}", logoPath);
            return null;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(bytes)) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String humanScope(ReportScope scope) {
        return switch (scope) {
            case PLATFORM -> "Platform-wide";
            case COMPANY -> "Company-wide";
            case PERSONAL -> "Personal";
        };
    }

    static String humanRole(String role) {
        return switch (role) {
            case "SUPER_ADMIN" -> "Platform Administrator";
            case "COMPANY_ADMIN" -> "Company Administrator";
            case "RECRUITER" -> "Recruiter";
            case "CANDIDATE" -> "Candidate";
            default -> role;
        };
    }

    /** Template errors nest several layers deep; the innermost message is the useful one. */
    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : e.toString();
    }

    private static String truncate(String s) {
        if (s == null) {
            return "Unknown error";
        }
        return s.length() > 900 ? s.substring(0, 900) : s;
    }

    private record Snapshot(ReportCatalog.Definition def,
                            String companyName,
                            String companyLogoPath,
                            String referenceNo,
                            String title,
                            String generatedByName,
                            String generatedByRole,
                            String approvedByName,
                            String approvedByRole,
                            Instant approvedAt,
                            LocalDate periodStart,
                            LocalDate periodEnd,
                            boolean reRender) {
    }
}
