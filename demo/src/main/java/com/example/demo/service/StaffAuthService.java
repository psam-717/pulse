package com.example.demo.service;

import com.example.demo.config.JwtUtil;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.WorkspaceSessionResponse;
import com.example.demo.model.StaffAccountStatus;
import com.example.demo.model.StaffMember;
import com.example.demo.repository.StaffMemberRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Facility-plane staff authentication (web dashboard). Issues JWTs bound
 * to exactly one facilityId (BACKEND_SPEC.md §2.3) with the staff role as
 * the claim. Legacy patient/hospital auth is untouched.
 */
@Service
public class StaffAuthService {

    private final StaffMemberRepository staffRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public StaffAuthService(StaffMemberRepository staffRepository, JwtUtil jwtUtil) {
        this.staffRepository = staffRepository;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        StaffMember staff = staffRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), staff.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (staff.getAccountStatus() == StaffAccountStatus.DEACTIVATED) {
            throw new IllegalArgumentException("This account has been deactivated");
        }

        String token = jwtUtil.generateStaffToken(
                staff.getId(), staff.getFacilityId(), staff.getRole().name());

        WorkspaceSessionResponse session = WorkspaceSessionResponse.from(staff);
        return new LoginResponse(token, session.role(), staff.getId(), "Login successful", session);
    }

    /** Resolves the session for an authenticated staff member (GET /auth/me). */
    public WorkspaceSessionResponse me(Long staffId) {
        StaffMember staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found"));
        return WorkspaceSessionResponse.from(staff);
    }
}
