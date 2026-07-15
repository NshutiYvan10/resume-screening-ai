package com.resumeai.web;

import com.resumeai.domain.enums.EmploymentType;
import com.resumeai.domain.enums.JobStatus;
import com.resumeai.domain.enums.WorkMode;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.JobDtos.*;
import com.resumeai.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    // -------------------------------------- public job board (no auth)

    @GetMapping("/public")
    public PageResponse<PublicJobResponse> listPublic(@RequestParam(required = false) String search,
                                                      @RequestParam(required = false) String location,
                                                      @RequestParam(required = false) EmploymentType employmentType,
                                                      @RequestParam(required = false) WorkMode workMode,
                                                      @RequestParam(required = false) UUID companyId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "12") int size) {
        return jobService.listPublic(search, location, employmentType, workMode, companyId, page, size);
    }

    @GetMapping("/public/{id}")
    public PublicJobResponse getPublic(@PathVariable UUID id) {
        return jobService.getPublic(id);
    }

    // ------------------------------------------------ company job desk

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public PageResponse<JobResponse> list(@RequestParam(required = false) JobStatus status,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return jobService.listCompanyJobs(status, search, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public JobResponse get(@PathVariable UUID id) {
        return jobService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public JobResponse create(@Valid @RequestBody JobRequest request) {
        return jobService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public JobResponse update(@PathVariable UUID id, @Valid @RequestBody JobRequest request) {
        return jobService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public void delete(@PathVariable UUID id) {
        jobService.delete(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public JobResponse submit(@PathVariable UUID id) {
        return jobService.submitForApproval(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public JobResponse approve(@PathVariable UUID id) {
        return jobService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public JobResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return jobService.reject(id, request.reason());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public JobResponse publish(@PathVariable UUID id) {
        return jobService.publishDirectly(id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public JobResponse close(@PathVariable UUID id) {
        return jobService.close(id);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public JobResponse archive(@PathVariable UUID id) {
        return jobService.archive(id);
    }
}
