package com.resumeai.web;

import com.resumeai.domain.enums.CompanyStatus;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.CompanyDtos.*;
import com.resumeai.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<CompanySummary> list(@RequestParam(required = false) String search,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return companyService.list(search, page, size);
    }

    /** Public candidate-facing company profile (no authentication required). */
    @GetMapping("/public/{id}")
    public PublicCompanyResponse getPublic(@PathVariable UUID id) {
        return companyService.getPublic(id);
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','RECRUITER')")
    public CompanyResponse myCompany() {
        return companyService.myCompany();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN','RECRUITER')")
    public CompanyResponse get(@PathVariable UUID id) {
        return companyService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public CompanyResponse update(@PathVariable UUID id, @Valid @RequestBody CompanyRequest request) {
        return companyService.update(id, request);
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public CompanyResponse uploadLogo(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return companyService.uploadLogo(id, file);
    }

    @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public CompanyResponse uploadCover(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return companyService.uploadCover(id, file);
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public CompanyResponse addPhoto(@PathVariable UUID id,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "caption", required = false) String caption) {
        return companyService.addPhoto(id, file, caption);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public CompanyResponse deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        return companyService.deletePhoto(id, photoId);
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanyResponse suspend(@PathVariable UUID id) {
        return companyService.setStatus(id, CompanyStatus.SUSPENDED);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanyResponse activate(@PathVariable UUID id) {
        return companyService.setStatus(id, CompanyStatus.ACTIVE);
    }
}
