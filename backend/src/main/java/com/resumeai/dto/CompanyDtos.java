package com.resumeai.dto;

import com.resumeai.domain.Company;
import com.resumeai.domain.enums.CompanyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class CompanyDtos {

    private CompanyDtos() {
    }

    public record CompanyRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 120) String industry,
            @Size(max = 255) String website,
            @Size(max = 50) String companySize,
            @Size(max = 200) String location,
            @Size(max = 5000) String description) {
    }

    public record CompanyResponse(
            UUID id,
            String name,
            String industry,
            String website,
            String companySize,
            String location,
            String description,
            CompanyStatus status,
            Instant createdAt) {

        public static CompanyResponse from(Company c) {
            return new CompanyResponse(c.getId(), c.getName(), c.getIndustry(), c.getWebsite(),
                    c.getCompanySize(), c.getLocation(), c.getDescription(), c.getStatus(), c.getCreatedAt());
        }
    }

    public record CompanySummary(
            UUID id,
            String name,
            String industry,
            String location,
            CompanyStatus status,
            long userCount,
            long jobCount,
            Instant createdAt) {
    }
}
