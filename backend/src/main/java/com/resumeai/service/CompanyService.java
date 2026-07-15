package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Company;
import com.resumeai.domain.enums.CompanyStatus;
import com.resumeai.domain.enums.Role;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.CompanyDtos.*;
import com.resumeai.repository.CompanyRepository;
import com.resumeai.repository.JobRepository;
import com.resumeai.repository.UserRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
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
        return CompanyResponse.from(find(companyId));
    }

    @Transactional(readOnly = true)
    public CompanyResponse myCompany() {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getCompanyId() == null) {
            throw ApiException.notFound("You are not associated with a company");
        }
        return CompanyResponse.from(find(actor.getCompanyId()));
    }

    @Transactional
    public CompanyResponse update(UUID companyId, CompanyRequest request) {
        requireAccess(companyId);
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() == Role.RECRUITER) {
            throw ApiException.forbidden("Only company administrators can update the company profile");
        }
        Company company = find(companyId);
        company.setName(request.name());
        company.setIndustry(request.industry());
        company.setWebsite(request.website());
        company.setCompanySize(request.companySize());
        company.setLocation(request.location());
        company.setDescription(request.description());

        auditService.log("COMPANY_UPDATED", "COMPANY", companyId.toString(),
                Map.of("name", request.name()));
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyResponse setStatus(UUID companyId, CompanyStatus status) {
        Company company = find(companyId);
        company.setStatus(status);
        auditService.log(status == CompanyStatus.SUSPENDED ? "COMPANY_SUSPENDED" : "COMPANY_ACTIVATED",
                "COMPANY", companyId.toString(), Map.of("name", company.getName()));
        return CompanyResponse.from(company);
    }

    private Company find(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("Company not found"));
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

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
