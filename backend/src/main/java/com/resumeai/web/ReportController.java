package com.resumeai.web;

import com.resumeai.domain.enums.ReportStatus;
import com.resumeai.domain.enums.ReportType;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.ReportDtos.*;
import com.resumeai.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Report generation, archive and sign-off. Every endpoint is role-gated coarsely here
 * and re-checked per report inside {@link ReportService}, which owns the visibility and
 * approval-authority rules.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final String ALL_ROLES = "hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN','RECRUITER','CANDIDATE')";

    private final ReportService reportService;

    /** Report types the calling user's role may generate. */
    @GetMapping("/types")
    @PreAuthorize(ALL_ROLES)
    public List<ReportTypeOption> types() {
        return reportService.availableTypes();
    }

    @PostMapping
    @PreAuthorize(ALL_ROLES)
    public ReportSummary generate(@Valid @RequestBody GenerateReportRequest request) {
        return reportService.generate(request);
    }

    /** Archive of previously generated reports, scoped to what the caller may see. */
    @GetMapping
    @PreAuthorize(ALL_ROLES)
    public PageResponse<ReportSummary> list(@RequestParam(required = false) ReportType type,
                                            @RequestParam(required = false) ReportStatus status,
                                            @RequestParam(required = false) Instant from,
                                            @RequestParam(required = false) Instant to,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return reportService.list(type, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ALL_ROLES)
    public ReportDetail get(@PathVariable UUID id) {
        return reportService.get(id);
    }

    /**
     * The rendered PDF. Served inline so the web app can preview it in an embedded
     * viewer, or as an attachment when {@code download=true}.
     */
    @GetMapping("/{id}/file")
    @PreAuthorize(ALL_ROLES)
    public ResponseEntity<Resource> file(@PathVariable UUID id,
                                         @RequestParam(defaultValue = "false") boolean download) {
        ReportService.Download doc = reportService.download(id);
        String disposition = (download ? "attachment" : "inline")
                + "; filename=\"" + doc.fileName() + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentLength(doc.bytes().length)
                .body(new ByteArrayResource(doc.bytes()));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN','RECRUITER')")
    public ReportDetail submit(@PathVariable UUID id) {
        return reportService.submitForApproval(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public ReportDetail approve(@PathVariable UUID id,
                                @Valid @RequestBody(required = false) DecisionRequest request) {
        return reportService.approve(id, request != null ? request.note() : null);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public ReportDetail reject(@PathVariable UUID id,
                               @Valid @RequestBody(required = false) DecisionRequest request) {
        return reportService.reject(id, request != null ? request.note() : null);
    }
}
