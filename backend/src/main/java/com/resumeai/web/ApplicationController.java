package com.resumeai.web;

import com.resumeai.domain.enums.ApplicationStatus;
import com.resumeai.dto.ApplicationDtos.ApplicationResponse;
import com.resumeai.dto.ApplicationDtos.StatusUpdateRequest;
import com.resumeai.dto.AuthDtos.MessageResponse;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    // ------------------------------------------------------- candidate

    @PostMapping(value = "/jobs/{jobId}/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApplicationResponse apply(@PathVariable UUID jobId,
                                     @RequestParam("resume") MultipartFile resume,
                                     @RequestParam(value = "coverLetter", required = false) String coverLetter) {
        return applicationService.apply(jobId, resume, coverLetter);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CANDIDATE')")
    public PageResponse<ApplicationResponse> myApplications(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return applicationService.myApplications(page, size);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApplicationResponse withdraw(@PathVariable UUID id) {
        return applicationService.withdraw(id);
    }

    // ------------------------------------------------------- recruiter

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public PageResponse<ApplicationResponse> listForJob(@PathVariable UUID jobId,
                                                        @RequestParam(required = false) ApplicationStatus status,
                                                        @RequestParam(required = false) BigDecimal minScore,
                                                        @RequestParam(defaultValue = "score") String sortBy,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return applicationService.listForJob(jobId, status, minScore, sortBy, page, size);
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable UUID id) {
        return applicationService.get(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public ApplicationResponse updateStatus(@PathVariable UUID id,
                                            @Valid @RequestBody StatusUpdateRequest request) {
        return applicationService.updateStatus(id, request);
    }

    @PostMapping("/{id}/rescreen")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public MessageResponse rescreen(@PathVariable UUID id) {
        applicationService.rescreen(id);
        return new MessageResponse("Screening has been queued");
    }

    @GetMapping("/{id}/resume")
    public ResponseEntity<Resource> downloadResume(@PathVariable UUID id) {
        ApplicationService.ResumeDownload download = applicationService.downloadResume(id);
        String contentType = download.contentType() != null
                ? download.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.fileName().replace("\"", "") + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(download.resource());
    }
}
