package com.resumeai.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.config.AppProperties;
import jakarta.annotation.PostConstruct;
import com.resumeai.domain.Application;
import com.resumeai.domain.JobQualification;
import com.resumeai.domain.ScreeningResult;
import com.resumeai.domain.enums.NotificationType;
import com.resumeai.domain.enums.ScreeningStatus;
import com.resumeai.repository.ApplicationRepository;
import com.resumeai.repository.ScreeningResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bridges applications to the Python AI microservice. Screening runs
 * asynchronously so candidate-facing submission stays fast; results land in
 * screening_results and the owning recruiter is notified in-app.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningService {

    private final ApplicationRepository applicationRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    private RestClient aiClient;

    @PostConstruct
    void initClient() {
        // Force HTTP/1.1: the JDK HttpClient otherwise attempts an h2c upgrade that the
        // FastAPI/uvicorn AI service rejects, corrupting the multipart upload.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.aiClient = RestClient.builder()
                .baseUrl(properties.getAiService().getBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Async
    public void queueScreening(UUID applicationId) {
        try {
            screen(applicationId);
        } catch (Exception e) {
            log.error("Screening pipeline crashed for application {}", applicationId, e);
        }
    }

    void screen(UUID applicationId) {
        // Phase 1: mark PROCESSING and gather everything we need
        ScreenJobData data = transactionTemplate.execute(status -> {
            Application application = applicationRepository.findById(applicationId).orElse(null);
            if (application == null) {
                return null;
            }
            ScreeningResult sr = application.getScreeningResult();
            if (sr == null) {
                sr = ScreeningResult.builder().application(application).build();
                application.setScreeningResult(sr);
            }
            sr.setStatus(ScreeningStatus.PROCESSING);
            sr.setErrorMessage(null);
            screeningResultRepository.save(sr);

            List<Map<String, Object>> quals = application.getJob().getQualifications().stream()
                    .map(this::toQualificationPayload)
                    .toList();
            return new ScreenJobData(
                    application.getResumeStoredPath(),
                    application.getResumeFileName(),
                    quals,
                    application.getJob().getDescription(),
                    application.getJob().getTitle(),
                    application.getJob().getMinExperienceYears(),
                    application.getJob().getEducationLevel() != null
                            ? application.getJob().getEducationLevel().name() : "",
                    application.getCandidate().getId(),
                    application.getCandidate().getFullName(),
                    application.getCandidate().getEmail(),
                    application.getCandidate().getPhone());
        });
        if (data == null) {
            log.warn("Application {} vanished before screening", applicationId);
            return;
        }

        // Phase 2: call the AI service outside any transaction
        AiScreenResponse ai;
        String error = null;
        try {
            ai = callAiService(data);
        } catch (Exception e) {
            log.error("AI screening failed for application {}: {}", applicationId, e.getMessage());
            ai = null;
            error = shorten(e.getMessage());
        }

        // Phase 3: persist the outcome
        final AiScreenResponse result = ai;
        final String failure = error;
        transactionTemplate.executeWithoutResult(status -> {
            Application application = applicationRepository.findById(applicationId).orElse(null);
            if (application == null) {
                return;
            }
            ScreeningResult sr = application.getScreeningResult();
            if (result != null) {
                sr.setStatus(ScreeningStatus.COMPLETED);
                sr.setMatchScore(result.matchScore());
                sr.setSkillsScore(result.scoreBreakdown().get("skills_match"));
                sr.setExperienceScore(result.scoreBreakdown().get("experience_match"));
                sr.setEducationScore(result.scoreBreakdown().get("education_match"));
                sr.setExtractedSkills(result.extractedSkills());
                sr.setExtractedEducation(result.extractedEducation());
                sr.setExtractedExperienceYears(result.extractedExperienceYears());
                sr.setBiasFlag(result.biasFlag());
                sr.setBiasFlagReason(result.biasFlagReason());
                sr.setMatchedSkills(result.matchedSkills());
                sr.setMissingRequired(result.missingRequired());
                sr.setMissingOptional(result.missingOptional());
                sr.setReasoning(result.reasoning());
                sr.setParseQuality(result.parseQuality());
                sr.setParseWarnings(result.parseWarnings());
                sr.setScreenedAt(Instant.now());

                // identity verification (advisory) + duplicate-resume detection
                sr.setExtractedName(result.extractedName());
                sr.setExtractedEmail(result.extractedEmail());
                sr.setExtractedPhone(result.extractedPhone());
                sr.setResumeFingerprint(result.resumeFingerprint());
                applyIdentity(sr, result, data.candidateId());

                if (application.getJob().getCreatedBy() != null) {
                    notificationService.notify(application.getJob().getCreatedBy(),
                            NotificationType.SCREENING_COMPLETED,
                            "AI screening completed",
                            application.getCandidate().getFullName() + " scored "
                                    + result.matchScore() + "/100 for "
                                    + application.getJob().getTitle() + ".",
                            "/company/jobs/" + application.getJob().getId() + "/applications",
                            false);
                }
            } else {
                sr.setStatus(ScreeningStatus.FAILED);
                sr.setErrorMessage(failure != null ? failure : "AI service unavailable");
            }
            screeningResultRepository.save(sr);
        });
        log.info("Screening finished for application {} -> {}", applicationId,
                result != null ? "COMPLETED" : "FAILED");
    }

    private AiScreenResponse callAiService(ScreenJobData data) throws Exception {
        byte[] bytes = Files.readAllBytes(fileStorageService.resolve(data.storedPath()));
        ByteArrayResource fileResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return data.fileName();
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        body.add("file", new HttpEntity<>(fileResource, fileHeaders));
        body.add("qualifications", objectMapper.writeValueAsString(data.qualifications()));
        body.add("job_description", data.jobDescription() != null ? data.jobDescription() : "");
        body.add("job_title", data.jobTitle() != null ? data.jobTitle() : "");
        // the job's actual requirements now drive experience/education scoring
        body.add("min_experience_years",
                data.minExperienceYears() != null ? data.minExperienceYears().toPlainString() : "0");
        body.add("education_level", data.educationLevel() != null ? data.educationLevel() : "");
        // applicant account identity → AI compares it against the resume (advisory)
        body.add("applicant_name", data.candidateName() != null ? data.candidateName() : "");
        body.add("applicant_email", data.candidateEmail() != null ? data.candidateEmail() : "");
        body.add("applicant_phone", data.candidatePhone() != null ? data.candidatePhone() : "");

        return aiClient
                .post()
                .uri("/api/v1/screen")
                .header("X-API-Key", properties.getAiService().getApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(AiScreenResponse.class);
    }

    /**
     * Fold the AI's advisory identity result plus a duplicate-resume check into
     * the screening row. Never touches the match score.
     */
    private void applyIdentity(ScreeningResult sr, AiScreenResponse result, UUID candidateId) {
        List<String> flags = new java.util.ArrayList<>(
                result.identityFlags() != null ? result.identityFlags() : List.of());
        boolean verified = result.identityVerified() == null || result.identityVerified();
        StringBuilder summary = new StringBuilder(
                result.identitySummary() != null ? result.identitySummary() : "");

        String fingerprint = result.resumeFingerprint();
        if (fingerprint != null && !fingerprint.isBlank()
                && screeningResultRepository.countOtherCandidatesWithFingerprint(fingerprint, candidateId) > 0) {
            flags.add("DUPLICATE_RESUME");
            verified = false;
            if (summary.length() > 0) {
                summary.append(' ');
            }
            summary.append("This exact resume has also been submitted by a different applicant.");
        }

        sr.setIdentityVerified(verified);
        sr.setIdentityFlags(flags.isEmpty() ? null : flags);
        sr.setIdentitySummary(summary.length() > 0 ? summary.toString() : null);
    }

    private Map<String, Object> toQualificationPayload(JobQualification q) {
        return Map.of(
                "skill", q.getSkill(),
                "weight", q.getWeight(),
                "required", q.isRequired());
    }

    private String shorten(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    /** Retry safety net: re-queue applications stuck in PENDING (e.g. app restarted mid-flight). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void retryPendingScreenings() {
        List<UUID> pending = transactionTemplate.execute(status ->
                screeningResultRepository.findAll().stream()
                        .filter(sr -> sr.getStatus() == ScreeningStatus.PENDING
                                && sr.getCreatedAt().isBefore(Instant.now().minusSeconds(120)))
                        .map(sr -> sr.getApplication().getId())
                        .limit(20)
                        .toList());
        if (pending != null && !pending.isEmpty()) {
            log.info("Retrying {} stuck screenings", pending.size());
            pending.forEach(this::queueScreening);
        }
    }

    record ScreenJobData(String storedPath, String fileName, List<Map<String, Object>> qualifications,
                         String jobDescription, String jobTitle,
                         BigDecimal minExperienceYears, String educationLevel,
                         UUID candidateId, String candidateName, String candidateEmail,
                         String candidatePhone) {
    }

    record AiScreenResponse(
            @JsonProperty("match_score") BigDecimal matchScore,
            @JsonProperty("score_breakdown") Map<String, BigDecimal> scoreBreakdown,
            @JsonProperty("extracted_skills") List<String> extractedSkills,
            @JsonProperty("extracted_education") String extractedEducation,
            @JsonProperty("extracted_experience_years") BigDecimal extractedExperienceYears,
            @JsonProperty("extracted_name") String extractedName,
            @JsonProperty("extracted_email") String extractedEmail,
            @JsonProperty("bias_flag") boolean biasFlag,
            @JsonProperty("bias_flag_reason") String biasFlagReason,
            @JsonProperty("matched_skills") List<String> matchedSkills,
            @JsonProperty("missing_required") List<String> missingRequired,
            @JsonProperty("missing_optional") List<String> missingOptional,
            @JsonProperty("reasoning") String reasoning,
            @JsonProperty("parse_quality") String parseQuality,
            @JsonProperty("parse_warnings") List<String> parseWarnings,
            @JsonProperty("extracted_phone") String extractedPhone,
            @JsonProperty("identity_verified") Boolean identityVerified,
            @JsonProperty("identity_flags") List<String> identityFlags,
            @JsonProperty("identity_summary") String identitySummary,
            @JsonProperty("resume_fingerprint") String resumeFingerprint) {
    }
}
