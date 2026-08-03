package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    // legacy binary .doc is rejected: the AI service cannot parse it, so accepting
    // it would produce guaranteed screening failures
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt");
    // company images are validated by content (see sniffImageExtension), not by filename
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;

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
                    + " - allowed formats: PDF, DOCX, TXT");
        }
        try {
            Path dir = root.resolve("resumes").resolve(companyId.toString());
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + "." + ext;
            Path target = dir.resolve(storedName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return toRelativeKey(target);
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store resume file");
        }
    }

    /**
     * Store a company profile image (logo/cover/gallery) under
     * storage-root/company-media/{companyId}/ and return the path relative to
     * the storage root.
     */
    public String storeCompanyImage(MultipartFile file, UUID companyId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("An image file is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw ApiException.badRequest("Image exceeds the maximum size of 5MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read the uploaded image");
        }
        // The filename extension is attacker/user controlled and routinely wrong (renaming
        // a HEIC to .png is a common way to get past a file picker filter). Trust the bytes
        // instead: an unreadable file stored as .png would be served as image/png and then
        // silently fail to render, which looks like "the upload did nothing".
        String ext = sniffImageExtension(bytes);
        if (ext == null) {
            String detected = describeRejectedFormat(bytes);
            throw ApiException.badRequest(detected != null
                    ? "This looks like " + detected + ", which browsers cannot display here - "
                            + "please convert it to PNG, JPG or WEBP and upload again"
                    : "That file is not a readable image - please upload a PNG, JPG or WEBP file");
        }
        if (!"webp".equals(ext) && !isDecodable(bytes)) {
            // magic bytes were right but the rest is truncated/corrupt
            throw ApiException.badRequest("That image appears to be incomplete or corrupted - "
                    + "please re-save it and upload again");
        }
        try {
            Path dir = root.resolve("company-media").resolve(companyId.toString());
            Files.createDirectories(dir);
            // stored extension comes from the sniffed type, so the Content-Type that
            // MediaController derives from it always matches the real bytes
            Path target = dir.resolve(UUID.randomUUID() + "." + ext);
            Files.write(target, bytes);
            return toRelativeKey(target);
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store image file");
        }
    }

    /** Image type implied by the magic bytes, or null if these bytes are not a supported image. */
    private static String sniffImageExtension(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return "png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    /**
     * Best-effort name for a rejected upload, so the error can tell the user what to
     * convert from instead of a generic failure. Null when the format is unrecognised.
     */
    private static String describeRejectedFormat(byte[] b) {
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            String brand = new String(b, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
            if (brand.startsWith("hei") || brand.startsWith("hev")
                    || brand.startsWith("mif") || brand.startsWith("msf")) {
                return "an iPhone/Mac HEIC photo";
            }
            if (brand.startsWith("avi")) {
                return "an AVIF image";
            }
            return "a video file";
        }
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "a GIF image";
        }
        if (b.length >= 2 && b[0] == 'B' && b[1] == 'M') {
            return "a BMP image";
        }
        if (b.length >= 4 && ((b[0] == 'I' && b[1] == 'I' && b[2] == 0x2A)
                || (b[0] == 'M' && b[1] == 'M' && b[3] == 0x2A))) {
            return "a TIFF image";
        }
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "a PDF document";
        }
        if (b.length >= 2 && (b[0] == 'P' && b[1] == 'K')) {
            return "a zip/office document";
        }
        String head = new String(b, 0, Math.min(b.length, 200), StandardCharsets.US_ASCII)
                .trim().toLowerCase(Locale.ROOT);
        if (head.startsWith("<?xml") || head.startsWith("<svg") || head.contains("<svg")) {
            return "an SVG image";
        }
        return null;
    }

    /** Confirms the bytes actually decode into a raster image (catches truncated files). */
    private static boolean isDecodable(byte[] bytes) {
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(bytes)) {
            return javax.imageio.ImageIO.read(in) != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Best-effort delete of a previously stored file; never throws. */
    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (Exception ignored) {
        }
    }

    public Path resolve(String relativePath) {
        // stored keys use '/' separators (see toRelativeKey); Path.resolve accepts
        // '/' on every OS including Windows, and legacy '\'-separated keys still
        // resolve correctly on the OS that produced them.
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw ApiException.badRequest("Invalid file path");
        }
        return path;
    }

    /**
     * Relative storage key with forward-slash separators, regardless of the host
     * OS. Windows {@code Path.toString()} uses backslashes, which would otherwise
     * corrupt URL building (the media URL splits on '/') and make stored keys
     * non-portable between machines.
     */
    private String toRelativeKey(Path target) {
        return root.relativize(target).toString().replace('\\', '/');
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
