package com.resumeai.web;

import com.resumeai.domain.enums.ApplicationStatus;
import com.resumeai.dto.ApplicationDtos.ApplicationResponse;
import com.resumeai.dto.AuthDtos.MessageResponse;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.PipelineDtos.*;
import com.resumeai.service.ApplicationService;
import com.resumeai.service.InterviewService;
import com.resumeai.service.OfferService;
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
    private final InterviewService interviewService;
    private final OfferService offerService;

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

    // --------------------------------------------------- admin oversight

    @GetMapping("/company")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public PageResponse<ApplicationResponse> companyPipeline(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) BigDecimal minScore,
            @RequestParam(defaultValue = "score") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return applicationService.companyPipeline(status, jobId, minScore, sortBy, page, size);
    }

    @GetMapping("/company/export")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<byte[]> exportCompanyPipeline() {
        byte[] csv = applicationService.exportCompanyPipelineCsv()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pipeline.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
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
    @PreAuthorize("hasAnyRole('CANDIDATE','COMPANY_ADMIN','RECRUITER')")
    public ApplicationResponse get(@PathVariable UUID id) {
        return applicationService.get(id);
    }

    // ------------------------------------------------ pipeline state machine

    @GetMapping("/{id}/pipeline")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public PipelineResponse pipeline(@PathVariable UUID id) {
        return applicationService.pipeline(id);
    }

    @PostMapping("/{id}/advance")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public ApplicationResponse advance(@PathVariable UUID id) {
        return applicationService.advanceStage(id);
    }

    @PostMapping("/{id}/backtrack")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ApplicationResponse backtrack(@PathVariable UUID id) {
        return applicationService.backtrackStage(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public ApplicationResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return applicationService.reject(id, request);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ApplicationResponse reopen(@PathVariable UUID id) {
        return applicationService.reopen(id);
    }

    @PostMapping("/{id}/hire")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public ApplicationResponse hire(@PathVariable UUID id) {
        return applicationService.markHired(id);
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public MessageResponse addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        applicationService.addNote(id, request);
        return new MessageResponse("Note added");
    }

    // -------------------------------------------------------- interviews

    @PostMapping("/{id}/interviews")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public MessageResponse scheduleInterview(@PathVariable UUID id,
                                             @Valid @RequestBody InterviewRequest request) {
        interviewService.schedule(id, request);
        return new MessageResponse("Interview scheduled");
    }

    @PostMapping("/interviews/{interviewId}/complete")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public MessageResponse completeInterview(@PathVariable UUID interviewId) {
        interviewService.complete(interviewId);
        return new MessageResponse("Interview marked completed");
    }

    @PostMapping("/interviews/{interviewId}/cancel")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public MessageResponse cancelInterview(@PathVariable UUID interviewId) {
        interviewService.cancel(interviewId);
        return new MessageResponse("Interview cancelled");
    }

    @PostMapping("/interviews/{interviewId}/feedback")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public MessageResponse submitFeedback(@PathVariable UUID interviewId,
                                          @Valid @RequestBody FeedbackRequest request) {
        interviewService.submitFeedback(interviewId, request);
        return new MessageResponse("Scorecard submitted");
    }

    // ------------------------------------------------------------- offers

    @PostMapping("/{id}/offer")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public MessageResponse createOffer(@PathVariable UUID id, @Valid @RequestBody OfferRequest request) {
        offerService.create(id, request);
        return new MessageResponse("Offer created");
    }

    @PutMapping("/offers/{offerId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public MessageResponse reviseOffer(@PathVariable UUID offerId,
                                       @Valid @RequestBody OfferRequest request) {
        offerService.revise(offerId, request);
        return new MessageResponse("Offer revised");
    }

    @PostMapping("/offers/{offerId}/approve")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public MessageResponse approveOffer(@PathVariable UUID offerId) {
        offerService.approve(offerId);
        return new MessageResponse("Offer approved");
    }

    @PostMapping("/offers/{offerId}/extend")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public MessageResponse extendOffer(@PathVariable UUID offerId) {
        offerService.extend(offerId);
        return new MessageResponse("Offer extended to the candidate");
    }

    @PostMapping("/offers/{offerId}/outcome")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public MessageResponse offerOutcome(@PathVariable UUID offerId,
                                        @Valid @RequestBody OfferOutcomeRequest request) {
        offerService.recordOutcome(offerId, request);
        return new MessageResponse("Offer outcome recorded");
    }

    @PostMapping("/{id}/rescreen")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public MessageResponse rescreen(@PathVariable UUID id) {
        applicationService.rescreen(id);
        return new MessageResponse("Screening has been queued");
    }

    @GetMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('CANDIDATE','COMPANY_ADMIN','RECRUITER')")
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
