package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Application;
import com.resumeai.domain.Interview;
import com.resumeai.domain.InterviewFeedback;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.*;
import com.resumeai.dto.PipelineDtos.FeedbackRequest;
import com.resumeai.dto.PipelineDtos.InterviewRequest;
import com.resumeai.repository.ApplicationRepository;
import com.resumeai.repository.InterviewFeedbackRepository;
import com.resumeai.repository.InterviewRepository;
import com.resumeai.repository.UserRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interview stage: scheduling with a panel, completion tracking, and
 * structured per-interviewer scorecards (independent feedback — panelists
 * cannot see each other's scorecards until they submit their own).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, MMM d yyyy 'at' HH:mm zzz").withZone(ZoneId.of("UTC"));

    private final InterviewRepository interviewRepository;
    private final InterviewFeedbackRepository feedbackRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ApplicationEventService eventService;

    @Transactional
    public UUID schedule(UUID applicationId, InterviewRequest request) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Application application = requireCompanyApplication(applicationId);
        if (application.getStatus() != ApplicationStatus.INTERVIEW) {
            throw ApiException.badRequest("Move the candidate to the Interview stage before scheduling");
        }
        if (request.scheduledAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Interview time must be in the future");
        }

        // panel must be active users of the same company
        List<User> panel = userRepository.findAllById(request.panelUserIds()).stream()
                .filter(u -> u.getCompany() != null
                        && u.getCompany().getId().equals(actor.getCompanyId())
                        && u.getStatus() == UserStatus.ACTIVE
                        && (u.getRole() == Role.COMPANY_ADMIN || u.getRole() == Role.RECRUITER))
                .toList();
        if (panel.isEmpty()) {
            throw ApiException.badRequest("Assign at least one active team member as interviewer");
        }

        Interview interview = Interview.builder()
                .application(application)
                .scheduledAt(request.scheduledAt())
                .durationMinutes(request.durationMinutes())
                .type(request.type())
                .location(request.location())
                .notes(request.notes())
                .createdBy(userRepository.getReferenceById(actor.getId()))
                .build();
        interview.getPanel().addAll(panel);
        interviewRepository.save(interview);

        String when = WHEN.format(request.scheduledAt());
        String candidateName = application.getCandidate().getFullName();
        String jobTitle = application.getJob().getTitle();

        eventService.record(application, ApplicationEventType.INTERVIEW_SCHEDULED, Map.of(
                "when", when, "type", request.type().name(),
                "panel", panel.stream().map(User::getFullName).toList()));
        auditService.log("INTERVIEW_SCHEDULED", "APPLICATION", applicationId.toString(),
                Map.of("candidate", application.getCandidate().getEmail(), "when", when));

        // action-needed: each panelist is told they're assigned
        for (User interviewer : panel) {
            notificationService.notify(interviewer, NotificationType.INTERVIEW_ASSIGNED,
                    "Interview assigned: " + candidateName,
                    "You are on the interview panel for " + candidateName + " (" + jobTitle + ") on "
                            + when + ". Submit your scorecard after the interview.",
                    "/company/applications/" + applicationId, true);
        }
        // candidate is invited
        String locationInfo = request.location() != null && !request.location().isBlank()
                ? " Location/link: " + request.location() + "." : "";
        notificationService.notify(application.getCandidate(), NotificationType.APPLICATION_STATUS_CHANGED,
                "Interview scheduled: " + jobTitle,
                "Your interview for " + jobTitle + " at " + application.getJob().getCompany().getName()
                        + " is scheduled for " + when + " (" + request.durationMinutes() + " minutes, "
                        + request.type().name().toLowerCase() + ")." + locationInfo,
                "/candidate/applications", true);

        return interview.getId();
    }

    @Transactional
    public void complete(UUID interviewId) {
        Interview interview = requireCompanyInterview(interviewId);
        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw ApiException.badRequest("Only scheduled interviews can be completed");
        }
        interview.setStatus(InterviewStatus.COMPLETED);
        eventService.record(interview.getApplication(), ApplicationEventType.INTERVIEW_COMPLETED,
                Map.of("when", WHEN.format(interview.getScheduledAt())));
    }

    @Transactional
    public void cancel(UUID interviewId) {
        Interview interview = requireCompanyInterview(interviewId);
        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw ApiException.badRequest("Only scheduled interviews can be cancelled");
        }
        interview.setStatus(InterviewStatus.CANCELLED);
        Application application = interview.getApplication();
        eventService.record(application, ApplicationEventType.INTERVIEW_CANCELLED,
                Map.of("when", WHEN.format(interview.getScheduledAt())));
        notificationService.notify(application.getCandidate(), NotificationType.APPLICATION_STATUS_CHANGED,
                "Interview cancelled: " + application.getJob().getTitle(),
                "Your interview scheduled for " + WHEN.format(interview.getScheduledAt())
                        + " has been cancelled. The hiring team will follow up with next steps.",
                "/candidate/applications", true);
    }

    /**
     * Panel member submits their independent scorecard. One per interviewer;
     * not editable afterwards (accountability).
     */
    @Transactional
    public void submitFeedback(UUID interviewId, FeedbackRequest request) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Interview interview = requireCompanyInterview(interviewId);

        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            throw ApiException.badRequest("This interview was cancelled");
        }
        boolean onPanel = interview.getPanel().stream()
                .anyMatch(u -> u.getId().equals(actor.getId()));
        if (!onPanel) {
            throw ApiException.forbidden("Only assigned panel members can submit feedback");
        }
        if (feedbackRepository.existsByInterviewIdAndInterviewerId(interviewId, actor.getId())) {
            throw ApiException.conflict("You already submitted feedback for this interview");
        }

        feedbackRepository.save(InterviewFeedback.builder()
                .interview(interview)
                .interviewer(userRepository.getReferenceById(actor.getId()))
                .rating(request.rating())
                .recommendation(request.recommendation())
                .strengths(request.strengths())
                .concerns(request.concerns())
                .build());

        Application application = interview.getApplication();
        eventService.record(application, ApplicationEventType.FEEDBACK_SUBMITTED, Map.of(
                "recommendation", request.recommendation().name(), "rating", request.rating()));

        // when the whole panel is in, tell the interview creator it's decision time
        long submitted = feedbackRepository.countForApplication(application.getId());
        boolean allIn = interview.getPanel().stream().allMatch(u ->
                feedbackRepository.existsByInterviewIdAndInterviewerId(interviewId, u.getId()));
        if (allIn && interview.getCreatedBy() != null
                && !interview.getCreatedBy().getId().equals(actor.getId())) {
            notificationService.notify(interview.getCreatedBy(), NotificationType.PIPELINE,
                    "All interview feedback in: " + application.getCandidate().getFullName(),
                    "Every panelist has submitted a scorecard for "
                            + application.getCandidate().getFullName() + " ("
                            + application.getJob().getTitle() + "). Review and decide next steps.",
                    "/company/applications/" + application.getId(), false);
        }
        log.info("Feedback submitted for interview {} ({} total for application)", interviewId, submitted);
    }

    /** Daily action-needed reminder for overdue scorecards. */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void remindPendingFeedback() {
        List<Interview> pending = interviewRepository
                .findWithPendingFeedback(Instant.now().minusSeconds(24 * 3600));
        for (Interview interview : pending) {
            Application application = interview.getApplication();
            for (User panelist : interview.getPanel()) {
                if (!feedbackRepository.existsByInterviewIdAndInterviewerId(
                        interview.getId(), panelist.getId())) {
                    notificationService.notify(panelist, NotificationType.FEEDBACK_DUE,
                            "Scorecard overdue: " + application.getCandidate().getFullName(),
                            "Your interview feedback for " + application.getCandidate().getFullName()
                                    + " (" + application.getJob().getTitle()
                                    + ") is still missing. Please submit your scorecard.",
                            "/company/applications/" + application.getId(), false);
                }
            }
        }
        if (!pending.isEmpty()) {
            log.info("Sent feedback reminders for {} interviews", pending.size());
        }
    }

    // ------------------------------------------------------------ helpers

    private Application requireCompanyApplication(UUID applicationId) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Application not found"));
        if (actor.getRole() != Role.COMPANY_ADMIN && actor.getRole() != Role.RECRUITER
                || !application.getJob().getCompany().getId().equals(actor.getCompanyId())) {
            throw ApiException.forbidden("This application belongs to another company");
        }
        return application;
    }

    private Interview requireCompanyInterview(UUID interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> ApiException.notFound("Interview not found"));
        requireCompanyApplication(interview.getApplication().getId());
        return interview;
    }
}
