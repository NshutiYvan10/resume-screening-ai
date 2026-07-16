package com.resumeai.dto;

import com.resumeai.domain.ApplicationEvent;
import com.resumeai.domain.Interview;
import com.resumeai.domain.InterviewFeedback;
import com.resumeai.domain.Offer;
import com.resumeai.domain.enums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PipelineDtos {

    private PipelineDtos() {
    }

    // ------------------------------------------------------------ requests

    public record NoteRequest(@NotBlank @Size(max = 4000) String text) {
    }

    public record RejectRequest(
            @NotNull RejectionReason reason,
            @Size(max = 4000) String internalNote,
            @Size(max = 2000) String candidateMessage) {
    }

    public record InterviewRequest(
            @NotNull Instant scheduledAt,
            @Min(15) @Max(480) int durationMinutes,
            @NotNull InterviewType type,
            @Size(max = 500) String location,
            @Size(max = 4000) String notes,
            @NotEmpty List<UUID> panelUserIds) {
    }

    public record FeedbackRequest(
            @Min(1) @Max(4) int rating,
            @NotNull FeedbackRecommendation recommendation,
            @Size(max = 4000) String strengths,
            @Size(max = 4000) String concerns) {
    }

    public record OfferRequest(
            @NotNull @DecimalMin("0") BigDecimal salary,
            @NotBlank @Size(max = 10) String currency,
            LocalDate startDate,
            LocalDate expiresAt,
            @Size(max = 4000) String notes) {
    }

    public record OfferOutcomeRequest(
            @NotNull OfferStatus status,   // ACCEPTED or DECLINED only
            @Size(max = 2000) String note) {
    }

    // ------------------------------------------------------------ responses

    public record PanelistResponse(UUID userId, String name, boolean feedbackSubmitted) {
    }

    public record FeedbackResponse(
            UUID id,
            UUID interviewerId,
            String interviewerName,
            Integer rating,
            FeedbackRecommendation recommendation,
            String strengths,
            String concerns,
            Instant submittedAt,
            boolean hidden) {

        public static FeedbackResponse from(InterviewFeedback f) {
            return new FeedbackResponse(f.getId(), f.getInterviewer().getId(),
                    f.getInterviewer().getFullName(), f.getRating(), f.getRecommendation(),
                    f.getStrengths(), f.getConcerns(), f.getSubmittedAt(), false);
        }

        /** Placeholder shown to panelists who haven't submitted their own feedback yet. */
        public static FeedbackResponse hidden(InterviewFeedback f) {
            return new FeedbackResponse(f.getId(), f.getInterviewer().getId(),
                    f.getInterviewer().getFullName(), null, null, null, null,
                    f.getSubmittedAt(), true);
        }
    }

    public record InterviewResponse(
            UUID id,
            Instant scheduledAt,
            int durationMinutes,
            InterviewType type,
            String location,
            String notes,
            InterviewStatus status,
            String createdByName,
            List<PanelistResponse> panel,
            List<FeedbackResponse> feedback,
            boolean viewerOnPanel,
            boolean viewerFeedbackSubmitted) {
    }

    public record OfferResponse(
            UUID id,
            BigDecimal salary,
            String currency,
            LocalDate startDate,
            LocalDate expiresAt,
            String notes,
            OfferStatus status,
            String createdByName,
            String approvedByName,
            Instant approvedAt,
            Instant extendedAt,
            Instant respondedAt) {

        public static OfferResponse from(Offer o) {
            return new OfferResponse(o.getId(), o.getSalary(), o.getCurrency(), o.getStartDate(),
                    o.getExpiresAt(), o.getNotes(), o.getStatus(),
                    o.getCreatedBy() != null ? o.getCreatedBy().getFullName() : null,
                    o.getApprovedBy() != null ? o.getApprovedBy().getFullName() : null,
                    o.getApprovedAt(), o.getExtendedAt(), o.getRespondedAt());
        }
    }

    public record EventResponse(
            Long id,
            ApplicationEventType type,
            String actorName,
            Map<String, Object> details,
            Instant createdAt) {

        public static EventResponse from(ApplicationEvent e) {
            return new EventResponse(e.getId(), e.getType(), e.getActorName(),
                    e.getDetails(), e.getCreatedAt());
        }
    }

    /** Composite payload for the pipeline workspace on the application page. */
    public record PipelineResponse(
            List<InterviewResponse> interviews,
            OfferResponse offer,
            List<EventResponse> timeline,
            List<String> allowedActions) {
    }
}
