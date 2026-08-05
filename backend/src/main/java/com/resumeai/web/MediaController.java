package com.resumeai.web;

import com.resumeai.common.exception.ApiException;
import com.resumeai.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Serves publicly visible company media (logos, covers, gallery photos).
 * Only files inside storage-root/company-media/{companyId}/ are reachable;
 * the single path segment cannot traverse directories.
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private static final Map<String, MediaType> CONTENT_TYPES = Map.of(
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "webp", MediaType.parseMediaType("image/webp"));

    private final FileStorageService fileStorageService;

    @GetMapping("/company/{companyId}/{fileName}")
    public ResponseEntity<Resource> serveCompanyMedia(@PathVariable UUID companyId,
                                                      @PathVariable String fileName) {
        return serve("company-media", companyId, fileName);
    }

    /**
     * Profile photos. Readable without a token so a plain {@code <img src>} works on
     * every surface that shows a person; the path carries two random UUIDs, so a URL
     * cannot be guessed from a user id alone. Who is *shown* a candidate's photo is
     * decided by the DTO layer, not here.
     */
    @GetMapping("/user/{userId}/{fileName}")
    public ResponseEntity<Resource> serveUserMedia(@PathVariable UUID userId,
                                                   @PathVariable String fileName) {
        return serve("user-media", userId, fileName);
    }

    private ResponseEntity<Resource> serve(String dir, UUID ownerId, String fileName) {
        if (fileName.contains("/") || fileName.contains("..")) {
            throw ApiException.badRequest("Invalid file name");
        }
        Path path = fileStorageService.resolve(dir + "/" + ownerId + "/" + fileName);
        FileSystemResource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw ApiException.notFound("File not found");
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        MediaType type = CONTENT_TYPES.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(resource);
    }
}
