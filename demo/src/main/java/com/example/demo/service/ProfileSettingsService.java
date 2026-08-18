package com.example.demo.service;

import com.example.demo.dto.settings.AdminProfileResponse;
import com.example.demo.dto.settings.UpdateProfileInput;
import com.example.demo.model.StaffMember;
import com.example.demo.repository.StaffMemberRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Profile (BACKEND_SPEC.md §5.8 / §6.7 rows 3-5). Shared by
 * admin and doctor roles — resolves the current staff member from the JWT
 * principal (StaffMember is the facility-plane user; the doctor's
 * name/title/specialty edits go through the Staff domain instead, but
 * password/preferences live here for every role).
 */
@Service
public class ProfileSettingsService {

    private final StaffMemberRepository staffRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public ProfileSettingsService(StaffMemberRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    public AdminProfileResponse get(Long staffId) {
        return toResponse(require(staffId));
    }

    @Transactional
    public AdminProfileResponse update(Long staffId, UpdateProfileInput input) {
        StaffMember s = require(staffId);

        if (input.fullName() != null) s.setName(input.fullName());
        if (input.title() != null) s.setTitle(input.title());
        if (input.phone() != null) s.setPhone(input.phone());
        if (input.avatarUrl() != null) s.setAvatarUrl(input.avatarUrl());

        UpdateProfileInput.NotificationPreferencesInput prefs = input.notificationPreferences();
        if (prefs != null) {
            if (prefs.emailOnNewAppointment() != null) s.setEmailOnNewAppointment(prefs.emailOnNewAppointment());
            if (prefs.emailOnNoShow() != null) s.setEmailOnNoShow(prefs.emailOnNoShow());
            if (prefs.smsOnQueueAlert() != null) s.setSmsOnQueueAlert(prefs.smsOnQueueAlert());
            if (prefs.dailySummaryEmail() != null) s.setDailySummaryEmail(prefs.dailySummaryEmail());
        }

        staffRepository.save(s);
        return toResponse(s);
    }

    @Transactional
    public void changePassword(Long staffId, String currentPassword, String newPassword) {
        StaffMember s = require(staffId);

        if (!passwordEncoder.matches(currentPassword, s.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
        if (passwordEncoder.matches(newPassword, s.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password.");
        }

        s.setPassword(passwordEncoder.encode(newPassword));
        staffRepository.save(s);
    }

    private StaffMember require(Long staffId) {
        if (staffId == null) {
            throw new IllegalArgumentException(
                    "This endpoint is only available to facility staff accounts (log in via /api/auth/login).");
        }
        return staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found for staff id " + staffId));
    }

    private AdminProfileResponse toResponse(StaffMember s) {
        return new AdminProfileResponse(
                s.getName(),
                s.getTitle(),
                s.getEmail(),
                s.getPhone(),
                s.getAvatarUrl(),
                new AdminProfileResponse.NotificationPreferencesDto(
                        s.isEmailOnNewAppointment(),
                        s.isEmailOnNoShow(),
                        s.isSmsOnQueueAlert(),
                        s.isDailySummaryEmail()));
    }
}
