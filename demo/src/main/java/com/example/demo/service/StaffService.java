package com.example.demo.service;

import com.example.demo.dto.CreateStaffRequest;
import com.example.demo.dto.StaffResponse;
import com.example.demo.dto.UpdateStaffRequest;
import com.example.demo.model.Department;
import com.example.demo.model.StaffAccountStatus;
import com.example.demo.model.StaffDutyStatus;
import com.example.demo.model.StaffMember;
import com.example.demo.model.StaffRole;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.StaffMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Facility-plane staff CRUD (BACKEND_SPEC.md §6.5) — tenant-scoped to the
 * caller's facilityId. Every new member starts on_duty / active (§7.6);
 * deactivation is a soft accountStatus flip via PATCH, never a delete.
 *
 * Passwords: CreateStaffInput has no password (the invite flow is a later
 * phase, §10.4), so new accounts get a random temporary password and cannot
 * log in until a password-set path exists.
 */
@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    private final StaffMemberRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public StaffService(StaffMemberRepository staffRepository,
                        DepartmentRepository departmentRepository) {
        this.staffRepository = staffRepository;
        this.departmentRepository = departmentRepository;
    }

    // ===== Read =====

    public List<StaffResponse> list(Long facilityId) {
        return staffRepository.findByFacilityId(facilityId).stream()
                .map(StaffResponse::from)
                .toList();
    }

    public StaffResponse get(Long facilityId, Long id) {
        return StaffResponse.from(findOwned(facilityId, id));
    }

    // ===== Write =====

    @Transactional
    public StaffResponse create(Long facilityId, CreateStaffRequest request) {
        String email = request.email().trim().toLowerCase();
        if (staffRepository.existsByEmailAndFacilityId(email, facilityId)) {
            throw new IllegalArgumentException(
                    "A staff member with email '" + request.email() + "' already exists in this facility");
        }

        StaffRole role = parseRole(request.role());
        StaffMember member = new StaffMember(
                request.name().trim(),
                role,
                request.title().trim(),
                request.specialty(),
                request.departmentId() != null ? request.departmentId() : "",
                resolveDepartmentName(facilityId, request.departmentId(), request.departmentName()),
                email,
                request.phone(),
                request.shiftStart(),
                request.shiftEnd(),
                StaffDutyStatus.ON_DUTY,        // §7.6: every new member starts on_duty
                StaffAccountStatus.ACTIVE,
                null,
                facilityId,
                passwordEncoder.encode(UUID.randomUUID().toString()) // temp — invite flow later
        );
        return StaffResponse.from(staffRepository.save(member));
    }

    @Transactional
    public StaffResponse update(Long facilityId, Long id, UpdateStaffRequest request) {
        StaffMember member = findOwned(facilityId, id);

        if (request.name() != null) member.setName(request.name().trim());
        if (request.role() != null) member.setRole(parseRole(request.role()));
        if (request.title() != null) member.setTitle(request.title().trim());
        if (request.specialty() != null) member.setSpecialty(request.specialty());
        if (request.departmentId() != null) {
            member.setDepartmentId(request.departmentId());
            member.setDepartmentName(resolveDepartmentName(
                    facilityId, request.departmentId(), request.departmentName()));
        } else if (request.departmentName() != null) {
            member.setDepartmentName(request.departmentName());
        }
        if (request.email() != null) {
            String email = request.email().trim().toLowerCase();
            if (!email.equals(member.getEmail())
                    && staffRepository.existsByEmailAndFacilityId(email, facilityId)) {
                throw new IllegalArgumentException(
                        "A staff member with email '" + request.email() + "' already exists in this facility");
            }
            member.setEmail(email);
        }
        if (request.phone() != null) member.setPhone(request.phone());
        if (request.shiftStart() != null) member.setShiftStart(request.shiftStart());
        if (request.shiftEnd() != null) member.setShiftEnd(request.shiftEnd());
        if (request.dutyStatus() != null) member.setDutyStatus(parseDutyStatus(request.dutyStatus()));
        if (request.accountStatus() != null) {
            member.setAccountStatus(parseAccountStatus(request.accountStatus()));
        }

        return StaffResponse.from(staffRepository.save(member));
    }

    // ===== Helpers =====

    private StaffMember findOwned(Long facilityId, Long id) {
        return staffRepository.findByIdAndFacilityId(id, facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found in this facility"));
    }

    /**
     * departmentName is derived from departmentId when it resolves to a real
     * department in the caller's facility; otherwise the client-sent value
     * is trusted (matches the mock — spec §10.4 open question).
     */
    private String resolveDepartmentName(Long facilityId, String departmentId, String clientName) {
        if (departmentId == null || departmentId.isBlank()) {
            return clientName != null ? clientName : "";
        }
        try {
            Long deptId = Long.parseLong(departmentId);
            return departmentRepository.findByIdAndFacilityId(deptId, facilityId)
                    .map(Department::getName)
                    .orElse(clientName != null ? clientName : "");
        } catch (NumberFormatException e) {
            return clientName != null ? clientName : "";
        }
    }

    static StaffRole parseRole(String role) {
        try {
            return StaffRole.valueOf(role.toUpperCase(Locale.ROOT).replace("-", "_"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid role '" + role + "'. Must be one of: admin, doctor, nurse, front-desk, read-only");
        }
    }

    static StaffDutyStatus parseDutyStatus(String value) {
        try {
            return StaffDutyStatus.valueOf(value.toUpperCase(Locale.ROOT).replace("-", "_"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid dutyStatus '" + value + "'. Must be one of: on_duty, off_duty, on_leave");
        }
    }

    static StaffAccountStatus parseAccountStatus(String value) {
        try {
            return StaffAccountStatus.valueOf(value.toUpperCase(Locale.ROOT).replace("-", "_"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid accountStatus '" + value + "'. Must be one of: active, deactivated");
        }
    }
}
