package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.CreateInviteRequest;
import com.example.demo.dto.RoleInviteResponse;
import com.example.demo.dto.UpdatePermissionRequest;
import com.example.demo.service.TeamAccessService;
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
 * Team & access — admin-only (BACKEND_SPEC §5.8 / §6.7 rows 17-21).
 */
@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasRole('ADMIN')")
public class TeamAccessController {

    private final TeamAccessService teamAccessService;

    public TeamAccessController(TeamAccessService teamAccessService) {
        this.teamAccessService = teamAccessService;
    }

    @GetMapping("/invites")
    public ResponseEntity<List<RoleInviteResponse>> invites() {
        return ResponseEntity.ok(teamAccessService.listInvites(SecurityUtils.requireFacilityId()));
    }

    @PostMapping("/invites")
    public ResponseEntity<RoleInviteResponse> createInvite(
            @Valid @RequestBody CreateInviteRequest request) {
        return ResponseEntity.ok(teamAccessService.createInvite(
                SecurityUtils.requireFacilityId(), request.email(), request.role()));
    }

    @DeleteMapping("/invites/{id}")
    public ResponseEntity<Void> cancelInvite(@PathVariable String id) {
        teamAccessService.cancelInvite(SecurityUtils.requireFacilityId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<TeamAccessService.MatrixRow>> permissionMatrix() {
        return ResponseEntity.ok(teamAccessService.permissionMatrix());
    }

    @PatchMapping("/permissions")
    public ResponseEntity<List<TeamAccessService.MatrixRow>> updatePermission(
            @Valid @RequestBody UpdatePermissionRequest request) {
        return ResponseEntity.ok(teamAccessService.updatePermission(
                request.resource(), request.role(), request.level()));
    }
}
