package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Company;
import com.resumeai.domain.CompanyPhoto;
import com.resumeai.domain.enums.CompanyStatus;
import com.resumeai.domain.enums.JobStatus;
import com.resumeai.domain.enums.Role;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.CompanyDtos.*;
import com.resumeai.repository.CompanyPhotoRepository;
import com.resumeai.repository.CompanyRepository;
import com.resumeai.repository.JobRepository;
import com.resumeai.repository.RefreshTokenRepository;
import com.resumeai.repository.UserRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final int MAX_GALLERY_PHOTOS = 12;

    private final CompanyRepository companyRepository;
    private final CompanyPhotoRepository companyPhotoRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<CompanySummary> list(String search, int page, int size) {
        return PageResponse.of(
                companyRepository.search(emptyToNull(search),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                c -> new CompanySummary(c.getId(), c.getName(), c.getIndustry(), c.getLocation(),
                        c.getStatus(), userRepository.countByCompanyId(c.getId()),
                        jobRepository.countByCompanyId(c.getId()), c.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(UUID companyId) {
        requireAccess(companyId);
        return CompanyResponse.from(find(companyId), photosOf(companyId));
    }

    /** Public company profile shown to candidates - active companies only. */
    @Transactional(readOnly = true)
    public PublicCompanyResponse getPublic(UUID companyId) {
        Company company = find(companyId);
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw ApiException.notFound("Company not found");
        }
        long openJobs = jobRepository.countOpenPublicJobs(companyId);
        return PublicCompanyResponse.from(company, photosOf(companyId), openJobs);
    }

    @Transactional(readOnly = true)
    public CompanyResponse myCompany() {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getCompanyId() == null) {
            throw ApiException.notFound("You are not associated with a company");
        }
        return CompanyResponse.from(find(actor.getCompanyId()), photosOf(actor.getCompanyId()));
    }

    @Transactional
    public CompanyResponse update(UUID companyId, CompanyRequest request) {
        Company company = requireEditable(companyId);
        company.setName(request.name());
        company.setIndustry(request.industry());
        company.setWebsite(request.website());
        company.setCompanySize(request.companySize());
        company.setLocation(request.location());
        company.setDescription(request.description());
        company.setTagline(request.tagline());
        company.setFoundedYear(request.foundedYear());
        company.setMission(request.mission());
        company.setValues(clean(request.values()));
        company.setBenefits(clean(request.benefits()));
        company.setLinkedinUrl(request.linkedinUrl());
        company.setTwitterUrl(request.twitterUrl());

        auditService.log("COMPANY_UPDATED", "COMPANY", companyId.toString(),
                Map.of("name", request.name()));
        return CompanyResponse.from(company, photosOf(companyId));
    }

    // ------------------------------------------------------------- media

    @Transactional
    public CompanyResponse uploadLogo(UUID companyId, MultipartFile file) {
        Company company = requireEditable(companyId);
        String path = fileStorageService.storeCompanyImage(file, companyId);
        fileStorageService.deleteQuietly(company.getLogoPath());
        company.setLogoPath(path);
        auditService.log("COMPANY_LOGO_UPDATED", "COMPANY", companyId.toString(), Map.of());
        return CompanyResponse.from(company, photosOf(companyId));
    }

    @Transactional
    public CompanyResponse uploadCover(UUID companyId, MultipartFile file) {
        Company company = requireEditable(companyId);
        String path = fileStorageService.storeCompanyImage(file, companyId);
        fileStorageService.deleteQuietly(company.getCoverPath());
        company.setCoverPath(path);
        auditService.log("COMPANY_COVER_UPDATED", "COMPANY", companyId.toString(), Map.of());
        return CompanyResponse.from(company, photosOf(companyId));
    }

    @Transactional
    public CompanyResponse addPhoto(UUID companyId, MultipartFile file, String caption) {
        Company company = requireEditable(companyId);
        if (companyPhotoRepository.countByCompanyId(companyId) >= MAX_GALLERY_PHOTOS) {
            throw ApiException.badRequest("Gallery is limited to " + MAX_GALLERY_PHOTOS
                    + " photos - remove one first");
        }
        String path = fileStorageService.storeCompanyImage(file, companyId);
        companyPhotoRepository.save(CompanyPhoto.builder()
                .company(company)
                .path(path)
                .caption(caption != null && caption.length() > 200 ? caption.substring(0, 200) : caption)
                .sortOrder((int) companyPhotoRepository.countByCompanyId(companyId))
                .build());
        auditService.log("COMPANY_PHOTO_ADDED", "COMPANY", companyId.toString(), Map.of());
        return CompanyResponse.from(company, photosOf(companyId));
    }

    @Transactional
    public CompanyResponse deletePhoto(UUID companyId, UUID photoId) {
        Company company = requireEditable(companyId);
        CompanyPhoto photo = companyPhotoRepository.findById(photoId)
                .orElseThrow(() -> ApiException.notFound("Photo not found"));
        if (!photo.getCompany().getId().equals(companyId)) {
            throw ApiException.forbidden("Photo belongs to another company");
        }
        fileStorageService.deleteQuietly(photo.getPath());
        companyPhotoRepository.delete(photo);
        auditService.log("COMPANY_PHOTO_REMOVED", "COMPANY", companyId.toString(), Map.of());
        return CompanyResponse.from(company, photosOf(companyId));
    }

    // ------------------------------------------------------------ status

    @Transactional
    public CompanyResponse setStatus(UUID companyId, CompanyStatus status) {
        Company company = find(companyId);
        company.setStatus(status);
        if (status == CompanyStatus.SUSPENDED) {
            // Cut off active sessions: revoke every refresh token belonging to the company's users.
            // Their access tokens are also rejected on the next request (the auth filter re-checks
            // company status live), and they cannot sign in again until reactivated.
            refreshTokenRepository.revokeAllForCompany(companyId, java.time.Instant.now());
        }
        auditService.log(status == CompanyStatus.SUSPENDED ? "COMPANY_SUSPENDED" : "COMPANY_ACTIVATED",
                "COMPANY", companyId.toString(), Map.of("name", company.getName()));
        return CompanyResponse.from(company, photosOf(companyId));
    }

    // ------------------------------------------------------------ helpers

    private List<CompanyPhoto> photosOf(UUID companyId) {
        return companyPhotoRepository.findByCompanyIdOrderBySortOrderAscCreatedAtAsc(companyId);
    }

    private Company find(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("Company not found"));
    }

    private Company requireEditable(UUID companyId) {
        requireAccess(companyId);
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() == Role.RECRUITER) {
            throw ApiException.forbidden("Only company administrators can update the company profile");
        }
        return find(companyId);
    }

    private void requireAccess(UUID companyId) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (!companyId.equals(actor.getCompanyId())) {
            throw ApiException.forbidden("You cannot access this company");
        }
    }

    private List<String> clean(List<String> items) {
        if (items == null) {
            return null;
        }
        return items.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
