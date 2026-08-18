package com.example.demo.controller;

import com.example.demo.config.FileStorageService;
import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.settings.UploadResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Generic image upload (BACKEND_SPEC.md §9.3) — avatar, facility logo,
 * HeFRA document, patient insurance card. Reuses {@link FileStorageService}
 * (extension whitelist, per-owner subdirectory, path-traversal guard) and
 * returns the stored URL relative to the /uploads/** static handler.
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
        Long facilityId = SecurityUtils.currentFacilityId();
        String url;
        if (facilityId != null) {
            url = fileStorageService.store(file, facilityId);
        } else if (isPatient()) {
            Long patientId = SecurityUtils.currentStaffId();
            if (patientId == null) {
                throw new AccessDeniedException(
                        "A patient or staff token is required to upload images.");
            }
            url = fileStorageService.store(file, "patients/" + patientId);
        } else {
            throw new AccessDeniedException(
                    "A patient or staff token is required to upload images.");
        }
        return ResponseEntity.ok(new UploadResponse(url));
    }

    private static boolean isPatient() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if ("ROLE_PATIENT".equals(a.getAuthority())) return true;
        }
        return false;
    }
}
