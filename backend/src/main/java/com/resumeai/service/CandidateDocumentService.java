package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.CandidateDocument;
import com.resumeai.domain.CoverLetterTemplate;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.DocumentKind;
import com.resumeai.domain.enums.Role;
import com.resumeai.dto.DocumentDtos.*;
import com.resumeai.repository.CandidateDocumentRepository;
import com.resumeai.repository.CoverLetterTemplateRepository;
import com.resumeai.repository.UserRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The candidate's own document library. Every method acts on the caller, so the actor id
 * <em>is</em> the authorisation and no tenant checks are needed.
 *
 * <p>Nothing here touches a submitted application. Applications hold their own copy of
 * the résumé bytes and the cover-letter text, so renaming, replacing or deleting a
 * library entry can never alter what a recruiter already screened.
 */
@Service
@RequiredArgsConstructor
public class CandidateDocumentService {

    /** Generous enough for real use, low enough that storage cannot run away. */
    static final int MAX_RESUMES = 10;
    static final int MAX_COVER_LETTERS = 10;

    private final CandidateDocumentRepository documentRepository;
    private final CoverLetterTemplateRepository coverLetterRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public LibraryResponse library() {
        UUID userId = candidateId();
        return new LibraryResponse(
                documentRepository.findByUserIdOrderByKindAscCreatedAtDesc(userId).stream()
                        .map(ResumeResponse::from).toList(),
                coverLetterRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(CoverLetterResponse::from).toList(),
                MAX_RESUMES, MAX_COVER_LETTERS);
    }

    // ------------------------------------------------------------- résumés

    @Transactional
    public ResumeResponse uploadResume(MultipartFile file, String label) {
        User user = candidate();
        if (documentRepository.countByUserIdAndKind(user.getId(), DocumentKind.RESUME) >= MAX_RESUMES) {
            throw ApiException.badRequest("You can keep up to " + MAX_RESUMES
                    + " résumés - delete one to add another");
        }
        FileStorageService.StoredDocument stored =
                fileStorageService.storeCandidateDocument(file, user.getId());
        boolean first = documentRepository.countByUserIdAndKind(user.getId(), DocumentKind.RESUME) == 0;
        CandidateDocument doc = CandidateDocument.builder()
                .user(user)
                .kind(DocumentKind.RESUME)
                .label(labelOr(label, file.getOriginalFilename()))
                .fileName(safeFileName(file.getOriginalFilename()))
                .storedPath(stored.path())
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .checksum(stored.checksum())
                // the first résumé becomes the default so applying works without a
                // separate "now pick a default" step
                .isDefault(first)
                .build();
        documentRepository.save(doc);
        auditService.log("RESUME_UPLOADED", "CANDIDATE_DOCUMENT", doc.getId().toString(),
                Map.of("label", doc.getLabel()));
        return ResumeResponse.from(doc);
    }

    @Transactional
    public ResumeResponse renameResume(UUID id, String label) {
        CandidateDocument doc = ownedResume(id);
        doc.setLabel(label.trim());
        auditService.log("RESUME_RENAMED", "CANDIDATE_DOCUMENT", id.toString(),
                Map.of("label", doc.getLabel()));
        return ResumeResponse.from(doc);
    }

    /** Swap the file, keeping the label, the default flag and the row identity. */
    @Transactional
    public ResumeResponse replaceResume(UUID id, MultipartFile file) {
        CandidateDocument doc = ownedResume(id);
        String previous = doc.getStoredPath();
        FileStorageService.StoredDocument stored =
                fileStorageService.storeCandidateDocument(file, doc.getUser().getId());
        doc.setStoredPath(stored.path());
        doc.setFileName(safeFileName(file.getOriginalFilename()));
        doc.setContentType(stored.contentType());
        doc.setSizeBytes(stored.sizeBytes());
        doc.setChecksum(stored.checksum());
        deleteAfterCommit(previous);
        auditService.log("RESUME_REPLACED", "CANDIDATE_DOCUMENT", id.toString(),
                Map.of("label", doc.getLabel()));
        return ResumeResponse.from(doc);
    }

    @Transactional
    public ResumeResponse setDefaultResume(UUID id) {
        CandidateDocument doc = ownedResume(id);
        // clear first: a partial unique index permits only one default per kind
        documentRepository.clearDefault(doc.getUser().getId(), DocumentKind.RESUME);
        documentRepository.flush();
        doc.setDefault(true);
        auditService.log("RESUME_SET_DEFAULT", "CANDIDATE_DOCUMENT", id.toString(), Map.of());
        return ResumeResponse.from(doc);
    }

    @Transactional
    public void deleteResume(UUID id) {
        CandidateDocument doc = ownedResume(id);
        UUID userId = doc.getUser().getId();
        boolean wasDefault = doc.isDefault();
        String path = doc.getStoredPath();
        documentRepository.delete(doc);
        documentRepository.flush();
        // promote another résumé so the candidate is not silently left without a default
        if (wasDefault) {
            documentRepository.findByUserIdOrderByKindAscCreatedAtDesc(userId).stream()
                    .filter(d -> d.getKind() == DocumentKind.RESUME)
                    .findFirst()
                    .ifPresent(next -> next.setDefault(true));
        }
        deleteAfterCommit(path);
        auditService.log("RESUME_DELETED", "CANDIDATE_DOCUMENT", id.toString(), Map.of());
    }

    /** Bytes for preview or download. Read into memory: capped at 10MB by the uploader. */
    @Transactional(readOnly = true)
    public ResumeDownload downloadResume(UUID id) {
        CandidateDocument doc = ownedResume(id);
        try {
            byte[] bytes = Files.readAllBytes(fileStorageService.resolve(doc.getStoredPath()));
            return new ResumeDownload(new ByteArrayResource(bytes), doc.getFileName(),
                    doc.getContentType() != null ? doc.getContentType() : "application/octet-stream");
        } catch (IOException e) {
            throw ApiException.notFound("That file is no longer available");
        }
    }

    public record ResumeDownload(Resource resource, String fileName, String contentType) {
    }

    // -------------------------------------------------------- cover letters

    @Transactional
    public CoverLetterResponse createCoverLetter(CoverLetterRequest request) {
        User user = candidate();
        if (coverLetterRepository.countByUserId(user.getId()) >= MAX_COVER_LETTERS) {
            throw ApiException.badRequest("You can keep up to " + MAX_COVER_LETTERS
                    + " cover letters - delete one to add another");
        }
        boolean first = coverLetterRepository.countByUserId(user.getId()) == 0;
        CoverLetterTemplate t = CoverLetterTemplate.builder()
                .user(user)
                .label(request.label().trim())
                .body(request.body())
                .isDefault(first)
                .build();
        coverLetterRepository.save(t);
        auditService.log("COVER_LETTER_CREATED", "COVER_LETTER", t.getId().toString(),
                Map.of("label", t.getLabel()));
        return CoverLetterResponse.from(t);
    }

    @Transactional
    public CoverLetterResponse updateCoverLetter(UUID id, CoverLetterRequest request) {
        CoverLetterTemplate t = ownedCoverLetter(id);
        t.setLabel(request.label().trim());
        t.setBody(request.body());
        auditService.log("COVER_LETTER_UPDATED", "COVER_LETTER", id.toString(),
                Map.of("label", t.getLabel()));
        return CoverLetterResponse.from(t);
    }

    @Transactional
    public CoverLetterResponse setDefaultCoverLetter(UUID id) {
        CoverLetterTemplate t = ownedCoverLetter(id);
        coverLetterRepository.clearDefault(t.getUser().getId());
        coverLetterRepository.flush();
        t.setDefault(true);
        auditService.log("COVER_LETTER_SET_DEFAULT", "COVER_LETTER", id.toString(), Map.of());
        return CoverLetterResponse.from(t);
    }

    @Transactional
    public void deleteCoverLetter(UUID id) {
        CoverLetterTemplate t = ownedCoverLetter(id);
        UUID userId = t.getUser().getId();
        boolean wasDefault = t.isDefault();
        coverLetterRepository.delete(t);
        coverLetterRepository.flush();
        if (wasDefault) {
            coverLetterRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .findFirst().ifPresent(next -> next.setDefault(true));
        }
        auditService.log("COVER_LETTER_DELETED", "COVER_LETTER", id.toString(), Map.of());
    }

    // ------------------------------------------------------------- internals

    private UUID candidateId() {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() != Role.CANDIDATE) {
            throw ApiException.forbidden("Only candidates have a document library");
        }
        return actor.getId();
    }

    private User candidate() {
        return userRepository.findById(candidateId())
                .orElseThrow(() -> ApiException.notFound("Your account could not be found"));
    }

    private CandidateDocument ownedResume(UUID id) {
        UUID userId = candidateId();
        CandidateDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Document not found"));
        if (!doc.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("That document belongs to someone else");
        }
        return doc;
    }

    private CoverLetterTemplate ownedCoverLetter(UUID id) {
        UUID userId = candidateId();
        CoverLetterTemplate t = coverLetterRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Cover letter not found"));
        if (!t.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("That cover letter belongs to someone else");
        }
        return t;
    }

    private void deleteAfterCommit(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorageService.deleteQuietly(relativePath);
            }
        });
    }

    private static String labelOr(String label, String fileName) {
        if (label != null && !label.isBlank()) {
            return label.trim().length() > 150 ? label.trim().substring(0, 150) : label.trim();
        }
        String name = safeFileName(fileName);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base.isBlank() ? "Résumé" : base;
    }

    /** Keep the DB happy (255) and strip path segments a browser may include. */
    private static String safeFileName(String original) {
        String name = original == null || original.isBlank() ? "document" : original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = slash >= 0 ? name.substring(slash + 1) : name;
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
