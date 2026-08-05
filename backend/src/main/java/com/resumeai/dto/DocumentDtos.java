package com.resumeai.dto;

import com.resumeai.domain.CandidateDocument;
import com.resumeai.domain.CoverLetterTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The candidate's document library: résumé files and saved cover-letter text. */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record ResumeResponse(UUID id,
                                 String label,
                                 String fileName,
                                 String contentType,
                                 Long sizeBytes,
                                 boolean isDefault,
                                 Instant createdAt,
                                 Instant updatedAt) {

        public static ResumeResponse from(CandidateDocument d) {
            return new ResumeResponse(d.getId(), d.getLabel(), d.getFileName(), d.getContentType(),
                    d.getSizeBytes(), d.isDefault(), d.getCreatedAt(), d.getUpdatedAt());
        }
    }

    public record CoverLetterResponse(UUID id,
                                      String label,
                                      String body,
                                      boolean isDefault,
                                      Instant createdAt,
                                      Instant updatedAt) {

        public static CoverLetterResponse from(CoverLetterTemplate t) {
            return new CoverLetterResponse(t.getId(), t.getLabel(), t.getBody(), t.isDefault(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    /** Everything the library page needs in one call. */
    public record LibraryResponse(List<ResumeResponse> resumes,
                                  List<CoverLetterResponse> coverLetters,
                                  int resumeLimit,
                                  int coverLetterLimit) {
    }

    public record RenameRequest(@NotBlank @Size(max = 150) String label) {
    }

    public record CoverLetterRequest(@NotBlank @Size(max = 150) String label,
                                     @NotBlank @Size(max = 20000) String body) {
    }
}
