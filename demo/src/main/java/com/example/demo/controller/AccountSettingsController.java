package com.example.demo.controller;

import com.example.demo.config.JwtUtil;
import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.AccountRequestResponse;
import com.example.demo.dto.ActiveSessionResponse;
import com.example.demo.dto.SubmitAccountRequestRequest;
import com.example.demo.dto.TwoFactorStatusResponse;
import com.example.demo.dto.UpdatePreferencesRequest;
import com.example.demo.dto.UserPreferencesResponse;
import com.example.demo.service.AccountSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Personal account settings — shared by all staff roles (BACKEND_SPEC §6.7
 * rows 8-16). The admin/doctor split in the web does not apply here: a
 * doctor's password/sessions/2FA/preferences/danger-zone go through the
 * same endpoints as an admin's.
 */
@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
public class AccountSettingsController {

    private final AccountSettingsService settingsService;
    private final JwtUtil jwtUtil;

    public AccountSettingsController(AccountSettingsService settingsService, JwtUtil jwtUtil) {
        this.settingsService = settingsService;
        this.jwtUtil = jwtUtil;
    }

    // ==================== Sessions ====================

    @GetMapping("/sessions")
    public ResponseEntity<List<ActiveSessionResponse>> sessions(HttpServletRequest request) {
        Long staffId = SecurityUtils.requireStaffId();
        return ResponseEntity.ok(settingsService.listSessions(staffId, currentSid(request)));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> signOutSession(@PathVariable String id,
                                               HttpServletRequest request) {
        settingsService.revokeSession(SecurityUtils.requireStaffId(), id, currentSid(request));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> signOutAllSessions(HttpServletRequest request) {
        settingsService.revokeAllSessions(SecurityUtils.requireStaffId(), currentSid(request));
        return ResponseEntity.noContent().build();
    }

    // ==================== 2FA ====================

    @GetMapping("/2fa")
    public ResponseEntity<TwoFactorStatusResponse> twoFactor() {
        return ResponseEntity.ok(settingsService.twoFactor(SecurityUtils.requireStaffId()));
    }

    @PatchMapping("/2fa")
    public ResponseEntity<TwoFactorStatusResponse> updateTwoFactor(
            @RequestBody TwoFactorToggleRequest body) {
        return ResponseEntity.ok(
                settingsService.setTwoFactor(SecurityUtils.requireStaffId(), body.enabled()));
    }

    // ==================== Preferences ====================

    @GetMapping("/preferences")
    public ResponseEntity<UserPreferencesResponse> preferences() {
        return ResponseEntity.ok(settingsService.preferences(SecurityUtils.requireStaffId()));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<UserPreferencesResponse> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request) {
        return ResponseEntity.ok(
                settingsService.updatePreferences(SecurityUtils.requireStaffId(), request));
    }

    // ==================== Danger zone ====================

    @GetMapping("/account-request")
    public ResponseEntity<AccountRequestResponse> accountRequest() {
        return ResponseEntity.ok(settingsService.accountRequest(SecurityUtils.requireStaffId()));
    }

    @PostMapping("/account-request")
    public ResponseEntity<AccountRequestResponse> submitAccountRequest(
            @Valid @RequestBody SubmitAccountRequestRequest request) {
        return ResponseEntity.ok(settingsService.submitAccountRequest(
                SecurityUtils.requireStaffId(), request.type(), request.transferOwnershipTo()));
    }

    // ==================== Helpers ====================

    private String currentSid(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        try {
            return jwtUtil.getSessionId(header.substring(7));
        } catch (Exception e) {
            return null;
        }
    }

    /** PATCH /settings/2fa body — { "enabled": boolean }. */
    public record TwoFactorToggleRequest(boolean enabled) {
    }
}
