package com.resumeai.dto;

import com.resumeai.domain.Company;
import com.resumeai.domain.CompanyPhoto;
import com.resumeai.domain.enums.CompanyStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
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
            @Size(max = 5000) String description,
            @Size(max = 200) String tagline,
            @Min(1800) @Max(2100) Integer foundedYear,
            @Size(max = 5000) String mission,
            @Size(max = 12) List<@Size(max = 60) String> values,
            @Size(max = 20) List<@Size(max = 120) String> benefits,
            @Size(max = 255) String linkedinUrl,
            @Size(max = 255) String twitterUrl) {
    }

    /** Builds the public URL for a stored media path (served by MediaController). */
    public static String mediaUrl(UUID companyId, String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        // stored keys use '/', but tolerate legacy Windows keys stored with '\'
        // so images uploaded before the separator fix still resolve.
        int slash = Math.max(storedPath.lastIndexOf('/'), storedPath.lastIndexOf('\\'));
        String fileName = storedPath.substring(slash + 1);
        return "/api/v1/media/company/" + companyId + "/" + fileName;
    }

    public record PhotoResponse(UUID id, String url, String caption) {
        public static PhotoResponse from(CompanyPhoto p) {
            return new PhotoResponse(p.getId(), mediaUrl(p.getCompany().getId(), p.getPath()), p.getCaption());
        }
    }

    public record CompanyResponse(
            UUID id,
            String name,
            String industry,
            String website,
            String companySize,
            String location,
            String description,
            String tagline,
            Integer foundedYear,
            String mission,
            List<String> values,
            List<String> benefits,
            String linkedinUrl,
            String twitterUrl,
            String logoUrl,
            String coverUrl,
            List<PhotoResponse> photos,
            CompanyStatus status,
            Instant createdAt) {

        public static CompanyResponse from(Company c, List<CompanyPhoto> photos) {
            return new CompanyResponse(c.getId(), c.getName(), c.getIndustry(), c.getWebsite(),
                    c.getCompanySize(), c.getLocation(), c.getDescription(), c.getTagline(),
                    c.getFoundedYear(), c.getMission(), c.getValues(), c.getBenefits(),
                    c.getLinkedinUrl(), c.getTwitterUrl(),
                    mediaUrl(c.getId(), c.getLogoPath()), mediaUrl(c.getId(), c.getCoverPath()),
                    photos.stream().map(PhotoResponse::from).toList(),
                    c.getStatus(), c.getCreatedAt());
        }
    }

    /** Candidate/public-facing profile - only information a company shares openly. */
    public record PublicCompanyResponse(
            UUID id,
            String name,
            String industry,
            String website,
            String companySize,
            String location,
            String description,
            String tagline,
            Integer foundedYear,
            String mission,
            List<String> values,
            List<String> benefits,
            String linkedinUrl,
            String twitterUrl,
            String logoUrl,
            String coverUrl,
            List<PhotoResponse> photos,
            long openJobs,
            Instant createdAt) {

        public static PublicCompanyResponse from(Company c, List<CompanyPhoto> photos, long openJobs) {
            return new PublicCompanyResponse(c.getId(), c.getName(), c.getIndustry(), c.getWebsite(),
                    c.getCompanySize(), c.getLocation(), c.getDescription(), c.getTagline(),
                    c.getFoundedYear(), c.getMission(), c.getValues(), c.getBenefits(),
                    c.getLinkedinUrl(), c.getTwitterUrl(),
                    mediaUrl(c.getId(), c.getLogoPath()), mediaUrl(c.getId(), c.getCoverPath()),
                    photos.stream().map(PhotoResponse::from).toList(),
                    openJobs, c.getCreatedAt());
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
