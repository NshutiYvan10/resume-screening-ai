package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class FileStorageService {

    // legacy binary .doc is rejected: the AI service cannot parse it, so accepting
    // it would produce guaranteed screening failures
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt");
    // company images are validated by content (see sniffImageExtension), not by filename
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;

    private final Path root;

    public FileStorageService(AppProperties properties) {
        this.root = resolveRoot(properties.getStorage().getRoot());
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create storage directory: " + root, e);
        }
        // logged because a wrong root is otherwise invisible: uploads succeed and only
        // later reads 404, which looks like data loss rather than a path problem
        log.info("File storage root: {}", root);
    }

    /**
     * Resolve the configured storage root to a stable absolute path.
     *
     * <p>A relative root (the {@code ./storage} default) is anchored to the application's
     * own directory rather than the process working directory. Otherwise starting the app
     * from the repository root instead of {@code backend/} silently points at a different,
     * empty store and every previously uploaded résumé, logo and report returns 404.
     * An absolute root is always honoured as given, which is what production should set.
     */
    private static Path resolveRoot(String configured) {
        Path configuredPath = Path.of(configured);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }
        return applicationHome().resolve(configuredPath).normalize();
    }

    /** The module directory holding the running classes or jar, independent of the shell's cwd. */
    private static Path applicationHome() {
        try {
            Path location = Path.of(FileStorageService.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                location = location.getParent();        // packaged jar -> its directory
            }
            if (isNamed(location, "classes")) {
                location = location.getParent();        // target/classes -> target
            }
            if (isNamed(location, "target")) {
                location = location.getParent();        // target -> backend
            }
            return location;
        } catch (Exception e) {
            // last resort: behave as before rather than fail to start
            return Path.of("").toAbsolutePath();
        }
    }

    private static boolean isNamed(Path path, String name) {
        return path != null && path.getFileName() != null
                && path.getFileName().toString().equals(name);
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
        return storeValidatedImage(file, "company-media", companyId);
    }

    /**
     * Shared image intake for every user-supplied picture: size cap, magic-byte
     * sniffing and a decode check, then written under {dir}/{ownerId}/ with an
     * extension derived from the sniffed type so the served Content-Type can never
     * contradict the bytes.
     */
    private String storeValidatedImage(MultipartFile file, String dir, UUID ownerId) {
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
        // The filename extension is user controlled and routinely wrong (renaming a HEIC
        // to .png is a common way past a file-picker filter). Trust the bytes instead: an
        // unreadable file stored as .png would be served as image/png and then silently
        // fail to render, which looks like "the upload did nothing".
        String ext = sniffImageExtension(bytes);
        if (ext == null) {
            String detected = describeRejectedFormat(bytes);
            throw ApiException.badRequest(detected != null
                    ? "This looks like " + detected + ", which browsers cannot display here - "
                            + "please convert it to PNG, JPG or WEBP and upload again"
                    : "That file is not a readable image - please upload a PNG, JPG or WEBP file");
        }
        if (!"webp".equals(ext) && !isDecodable(bytes)) {
            throw ApiException.badRequest("That image appears to be incomplete or corrupted - "
                    + "please re-save it and upload again");
        }
        try {
            Path target = root.resolve(dir).resolve(ownerId.toString());
            Files.createDirectories(target);
            Path file2 = target.resolve(UUID.randomUUID() + "." + ext);
            Files.write(file2, bytes);
            return toRelativeKey(file2);
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store image file");
        }
    }

    /**
     * Store a user's profile photo under storage-root/user-media/{userId}/.
     *
     * <p>Partitioned by user rather than company because candidates have no company,
     * so the company-media layout cannot hold their avatars.
     */
    public String storeUserImage(MultipartFile file, UUID userId) {
        return storeValidatedImage(file, "user-media", userId);
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

    /**
     * Store (or replace) a generated report PDF under storage-root/reports/ and return
     * the path relative to the storage root. Reports are keyed by their own id and are
     * overwritten in place when a document is re-rendered on approval.
     *
     * <p>Unlike company media these are never publicly served - every read goes through
     * an authorised endpoint - so they are not partitioned by tenant on disk.
     */
    public String storeReport(byte[] pdf, UUID reportId) {
        try {
            Path dir = root.resolve("reports");
            Files.createDirectories(dir);
            Path target = dir.resolve(reportId + ".pdf");
            Files.write(target, pdf);
            return toRelativeKey(target);
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store the generated report");
        }
    }

    /** A stored library document: everything the row needs to describe the file. */
    public record StoredDocument(String path, String checksum, long sizeBytes, String contentType) {
    }

    /**
     * Store a résumé in a candidate's document library under
     * storage-root/candidate-documents/{userId}/.
     *
     * <p>Separate from {@link #storeResume} because a library belongs to the candidate,
     * not to a company - candidates have no company, so the company-partitioned résumé
     * path cannot hold these. The checksum lets an identical re-upload be recognised.
     */
    public StoredDocument storeCandidateDocument(MultipartFile file, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("A document file is required");
        }
        if (file.getSize() > MAX_DOCUMENT_BYTES) {
            throw ApiException.badRequest("Document exceeds the maximum size of 10MB");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String ext = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw ApiException.badRequest("Unsupported document format ." + ext
                    + " - allowed formats: PDF, DOCX, TXT");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read the uploaded document");
        }
        try {
            Path dir = root.resolve("candidate-documents").resolve(userId.toString());
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + ext);
            Files.write(target, bytes);
            return new StoredDocument(toRelativeKey(target), sha256(bytes), bytes.length,
                    file.getContentType());
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store the document");
        }
    }

    /**
     * Copy a stored file to a new key under the same root. Used when applying: the
     * application takes its own immutable copy of the library document, so editing or
     * deleting the library entry later cannot rewrite screening evidence.
     */
    public String copyTo(String sourceRelativePath, String targetPrefix, UUID scopeId) {
        Path source = resolve(sourceRelativePath);
        if (!Files.exists(source)) {
            throw ApiException.notFound("That document is no longer available");
        }
        String name = source.getFileName().toString();
        String ext = extensionOf(name);
        try {
            Path dir = root.resolve(targetPrefix).resolve(scopeId.toString());
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext));
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return toRelativeKey(target);
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to attach the document to the application");
        }
    }

    /** Hex SHA-256, matching the checksum format used for generated reports. */
    public static String sha256(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
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
