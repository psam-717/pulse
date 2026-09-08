package com.example.demo.service;

import com.example.demo.dto.RoleInviteResponse;
import com.example.demo.model.PermissionOverride;
import com.example.demo.model.StaffInvite;
import com.example.demo.model.StaffRole;
import com.example.demo.repository.PermissionOverrideRepository;
import com.example.demo.repository.StaffInviteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Team & access (BACKEND_SPEC §5.8 / §6.7 rows 17-21): invites + permission
 * matrix. Matrix defaults mirror the web seed exactly (7 resources × 5
 * roles); admin changes are stored as PermissionOverride cells and merged
 * over the defaults on read.
 */
@Service
public class TeamAccessService {

    public static final List<String> ROLE_NAMES = List.of(
            "admin", "doctor", "nurse", "front_desk", "read_only");
    public static final Set<String> LEVELS = Set.of("none", "view", "edit");

    private static final Map<String, Map<String, String>> DEFAULTS = defaults();

    private final StaffInviteRepository inviteRepository;
    private final PermissionOverrideRepository overrideRepository;

    public TeamAccessService(StaffInviteRepository inviteRepository,
                             PermissionOverrideRepository overrideRepository) {
        this.inviteRepository = inviteRepository;
        this.overrideRepository = overrideRepository;
    }

    // ==================== Invites ====================

    @Transactional(readOnly = true)
    public List<RoleInviteResponse> listInvites(Long facilityId) {
        return inviteRepository.findByFacilityIdAndStatusOrderByInvitedAtDesc(facilityId, "pending")
                .stream().map(RoleInviteResponse::from).toList();
    }

    @Transactional
    public RoleInviteResponse createInvite(Long facilityId, String email, String rawRole) {
        String role = normalizeRole(rawRole);
        String lowerEmail = email.trim().toLowerCase();
        if (inviteRepository.findFirstByFacilityIdAndEmailAndStatus(facilityId, lowerEmail, "pending").isPresent()) {
            throw new IllegalArgumentException("An invite is already pending for that email");
        }
        StaffInvite invite = new StaffInvite(UUID.randomUUID().toString(), facilityId,
                lowerEmail, role.toUpperCase());
        return RoleInviteResponse.from(inviteRepository.save(invite));
    }

    @Transactional
    public void cancelInvite(Long facilityId, String publicId) {
        StaffInvite invite = inviteRepository.findByPublicIdAndStatus(publicId, "pending")
                .orElseThrow(() -> new IllegalArgumentException("Invite not found"));
        if (!invite.getFacilityId().equals(facilityId)) {
            throw new IllegalArgumentException("Invite not found");
        }
        invite.setStatus("cancelled");
        inviteRepository.save(invite);
    }

    // ==================== Permission matrix ====================

    @Transactional(readOnly = true)
    public List<MatrixRow> permissionMatrix() {
        List<PermissionOverride> overrides = overrideRepository.findAll();
        Map<String, Map<String, String>> levels = new LinkedHashMap<>();
        DEFAULTS.forEach((resource, byRole) -> levels.put(resource, new LinkedHashMap<>(byRole)));
        for (PermissionOverride o : overrides) {
            Map<String, String> cell = levels.get(o.getResource());
            if (cell != null) {
                cell.put(o.getRoleName(), o.getLevel());
            }
        }
        List<MatrixRow> rows = new ArrayList<>();
        levels.forEach((resource, byRole) -> rows.add(new MatrixRow(resource, byRole)));
        return rows;
    }

    @Transactional
    public List<MatrixRow> updatePermission(String resource, String rawRole, String level) {
        String role = normalizeRole(rawRole);
        if (!LEVELS.contains(level)) {
            throw new IllegalArgumentException("level must be one of: none, view, edit");
        }
        if (!DEFAULTS.containsKey(resource)) {
            throw new IllegalArgumentException("Unknown permission resource: " + resource);
        }
        PermissionOverride override = overrideRepository
                .findByResourceAndRoleName(resource, role)
                .orElseGet(() -> new PermissionOverride(resource, role, level));
        override.setLevel(level);
        overrideRepository.save(override);
        return permissionMatrix();
    }

    private static String normalizeRole(String rawRole) {
        String role = rawRole.trim().toLowerCase();
        if (!ROLE_NAMES.contains(role)) {
            throw new IllegalArgumentException("role must be one of: admin, doctor, nurse, front_desk, read_only");
        }
        return role;
    }

    public record MatrixRow(String resource, Map<String, String> permissions) {
    }

    private static Map<String, Map<String, String>> defaults() {
        Map<String, Map<String, String>> m = new LinkedHashMap<>();
        m.put("Departments", cell("edit", "view", "view", "view", "view"));
        m.put("Live Queue", cell("edit", "edit", "edit", "edit", "view"));
        m.put("Appointments", cell("edit", "edit", "edit", "edit", "view"));
        m.put("Patients", cell("edit", "edit", "edit", "edit", "view"));
        m.put("Staff & Doctors", cell("edit", "view", "view", "view", "view"));
        m.put("Analytics", cell("edit", "view", "none", "none", "view"));
        m.put("Settings", cell("edit", "none", "none", "none", "none"));
        return m;
    }

    private static Map<String, String> cell(String admin, String doctor, String nurse,
                                            String frontDesk, String readOnly) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("admin", admin);
        map.put("doctor", doctor);
        map.put("nurse", nurse);
        map.put("front_desk", frontDesk);
        map.put("read_only", readOnly);
        return map;
    }
}
