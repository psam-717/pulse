package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Handles file storage for uploaded license documents.
 * Files are stored under uploadDir/{hospitalId}/{uuid}.{ext}
 * and served via a static resource handler at /uploads/**.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "gif", "webp"
    );

    private final Path uploadDir;

    public FileStorageService(@Value("${file.upload.dir:uploads/licenses}") String uploadDirPath) {
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(uploadDir);
            log.info("License upload directory: {}", uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + uploadDir, e);
        }
    }

    /**
     * Store a license document for a hospital.
     *
     * @param file       The uploaded file
     * @param hospitalId The hospital ID
     * @return The relative URL path to access the file (e.g. "/uploads/1/uuid.pdf")
     */
    public String store(MultipartFile file, Long hospitalId) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        String storedName = UUID.randomUUID() + "." + extension;

        // Create hospital subdirectory
        Path hospitalDir = uploadDir.resolve(String.valueOf(hospitalId));
        try {
            Files.createDirectories(hospitalDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create hospital upload directory", e);
        }

        Path targetPath = hospitalDir.resolve(storedName).normalize();

        // Ensure the resolved path is still within the upload directory (path traversal check)
        if (!targetPath.startsWith(uploadDir)) {
            throw new SecurityException("Invalid file path");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("License file stored: {} (hospitalId={}, size={} bytes)",
                    storedName, hospitalId, file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + originalFilename, e);
        }

        return "/uploads/" + hospitalId + "/" + storedName;
    }

    /**
     * Delete a previously stored file by its relative URL path.
     */
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        // Extract the relative path from the URL (/uploads/hospitalId/filename)
        String relativePath = fileUrl.startsWith("/uploads/")
                ? fileUrl.substring("/uploads/".length())
                : fileUrl;

        Path filePath = uploadDir.resolve(relativePath).normalize();
        if (!filePath.startsWith(uploadDir)) {
            log.warn("Attempted to delete file outside upload directory: {}", fileUrl);
            return;
        }

        try {
            Files.deleteIfExists(filePath);
            log.info("Deleted license file: {}", filePath);
        } catch (IOException e) {
            log.warn("Could not delete file: {}", filePath, e);
        }
    }

    /**
     * Get the absolute path of the upload directory (with trailing slash).
     */
    public String getUploadDir() {
        return uploadDir.toString().replace("\\", "/") + "/";
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File has no name");
        }

        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException(
                    "File type '" + extension + "' is not allowed. Accepted: " + ALLOWED_EXTENSIONS);
        }

        // 10MB cap
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds the 10MB limit");
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "bin";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }
}