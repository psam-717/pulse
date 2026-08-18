package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.settings.AdminProfileResponse;
import com.example.demo.dto.settings.ChangePasswordInput;
import com.example.demo.dto.settings.UpdateProfileInput;
import com.example.demo.service.ProfileSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Settings → Profile (BACKEND_SPEC.md §6.7 rows 3-5). Shared by admin and
 * doctor roles — any authenticated staff member edits their own profile.
 */
@RestController
@RequestMapping("/api/settings/profile")
public class ProfileSettingsController {

    private final ProfileSettingsService profileSettingsService;

    public ProfileSettingsController(ProfileSettingsService profileSettingsService) {
        this.profileSettingsService = profileSettingsService;
    }

    @GetMapping
    public ResponseEntity<AdminProfileResponse> get() {
        return ResponseEntity.ok(profileSettingsService.get(SecurityUtils.currentStaffId()));
    }

    @PatchMapping
    public ResponseEntity<AdminProfileResponse> update(@RequestBody UpdateProfileInput input) {
        return ResponseEntity.ok(profileSettingsService.update(SecurityUtils.currentStaffId(), input));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordInput input) {
        profileSettingsService.changePassword(SecurityUtils.currentStaffId(),
                input.currentPassword(), input.newPassword());
        return ResponseEntity.ok().build();
    }
}
