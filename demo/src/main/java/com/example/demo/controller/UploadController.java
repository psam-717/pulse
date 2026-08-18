package com.example.demo.controller;

import com.example.demo.config.FileStorageService;
import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.settings.UploadResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Generic image upload (BACKEND_SPEC.md §9.3) — avatar, facility logo,
 * HeFRA document. Reuses {@link FileStorageService} (extension/MIME
 * whitelist, per-facility subdirectory, path-traversal guard) and returns
 * the stored URL relative to the /uploads/** static handler.
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final FileStorageService fileStorageService;

    public UploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/images")
    public ResponseEntity<UploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        Long facilityId = SecurityUtils.requireFacilityId();
        String url = fileStorageService.store(file, facilityId);
        return ResponseEntity.ok(new UploadResponse(url));
    }
}
