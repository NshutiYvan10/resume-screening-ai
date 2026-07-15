package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "doc", "txt");

    private final Path root;

    public FileStorageService(AppProperties properties) {
        this.root = Path.of(properties.getStorage().getRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create storage directory: " + root, e);
        }
    }

    /**
     * Store a resume under storage-root/resumes/{companyId}/ and return the
     * path relative to the storage root.
     */
    public String storeResume(MultipartFile file, UUID companyId) {
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "resume";
        String ext = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw ApiException.badRequest("Unsupported resume format ." + ext
                    + " - allowed formats: PDF, DOCX, DOC, TXT");
        }
        try {
            Path dir = root.resolve("resumes").resolve(companyId.toString());
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + "." + ext;
            Path target = dir.resolve(storedName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return root.relativize(target).toString();
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store resume file");
        }
    }

    public Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw ApiException.badRequest("Invalid file path");
        }
        return path;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
