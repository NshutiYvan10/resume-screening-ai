package com.resumeai.web;

import com.resumeai.dto.DocumentDtos.*;
import com.resumeai.service.CandidateDocumentService;
import com.resumeai.service.CandidateDocumentService.ResumeDownload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The candidate's document library. Everything acts on the caller, so no id appears in
 * the path for ownership purposes - the service re-checks ownership on every lookup.
 */
@RestController
@RequestMapping("/api/v1/documents")
@PreAuthorize("hasRole('CANDIDATE')")
@RequiredArgsConstructor
public class CandidateDocumentController {

    private final CandidateDocumentService documentService;

    @GetMapping
    public LibraryResponse library() {
        return documentService.library();
    }

    /** How each saved résumé has performed, derived from real screenings. */
    @GetMapping("/insights")
    public InsightsResponse insights() {
        return documentService.insights();
    }

    // ---------------------------------------------------------------- résumés

    @PostMapping(value = "/resumes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeResponse upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "label", required = false) String label) {
        return documentService.uploadResume(file, label);
    }

    @PatchMapping("/resumes/{id}")
    public ResumeResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        return documentService.renameResume(id, request.label());
    }

    @PostMapping(value = "/resumes/{id}/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeResponse replace(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return documentService.replaceResume(id, file);
    }

    @PostMapping("/resumes/{id}/default")
    public ResumeResponse setDefault(@PathVariable UUID id) {
        return documentService.setDefaultResume(id);
    }

    @DeleteMapping("/resumes/{id}")
    public void delete(@PathVariable UUID id) {
        documentService.deleteResume(id);
    }

    /**
     * Preview inline by default so the in-app viewer can embed it; {@code download=true}
     * forces the browser's save dialog instead.
     */
    @GetMapping("/resumes/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable UUID id,
                                         @RequestParam(defaultValue = "false") boolean download) {
        ResumeDownload doc = documentService.downloadResume(id);
        String disposition = (download ? "attachment" : "inline")
                + "; filename*=UTF-8''" + encode(doc.fileName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .body(doc.resource());
    }

    // ----------------------------------------------------------- cover letters

    @PostMapping("/cover-letters")
    public CoverLetterResponse createCoverLetter(@Valid @RequestBody CoverLetterRequest request) {
        return documentService.createCoverLetter(request);
    }

    @PutMapping("/cover-letters/{id}")
    public CoverLetterResponse updateCoverLetter(@PathVariable UUID id,
                                                 @Valid @RequestBody CoverLetterRequest request) {
        return documentService.updateCoverLetter(id, request);
    }

    @PostMapping("/cover-letters/{id}/default")
    public CoverLetterResponse setDefaultCoverLetter(@PathVariable UUID id) {
        return documentService.setDefaultCoverLetter(id);
    }

    @DeleteMapping("/cover-letters/{id}")
    public void deleteCoverLetter(@PathVariable UUID id) {
        documentService.deleteCoverLetter(id);
    }

    /**
     * RFC 5987 encoding. The existing résumé download only strips quotes, which lets a
     * CR/LF or non-ASCII filename corrupt the header; percent-encoding avoids both.
     */
    private static String encode(String fileName) {
        StringBuilder sb = new StringBuilder();
        for (byte b : fileName.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean safe = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_';
            if (safe) {
                sb.append((char) c);
            } else {
                sb.append('%').append(Character.forDigit(c >> 4, 16))
                        .append(Character.forDigit(c & 0xF, 16));
            }
        }
        return sb.toString();
    }
}
