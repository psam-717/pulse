package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.CallNextRequest;
import com.example.demo.dto.QueueDepartmentResponse;
import com.example.demo.dto.QueueEntryResponse;
import com.example.demo.dto.UpdateQueueStatusRequest;
import com.example.demo.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Live Queue (BACKEND_SPEC.md §6.4) — read for all facility staff with the
 * queue in scope, writes for admin/doctor/nurse/front-desk.
 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
    public ResponseEntity<List<QueueDepartmentResponse>> departments() {
        Long facilityId = SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(queueService.departments(facilityId));
    }

    @GetMapping("/entries")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
    public ResponseEntity<List<QueueEntryResponse>> entries(
            @RequestParam String departmentId) {
        SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(queueService.entries(departmentId));
    }

    @PostMapping("/call-next")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK')")
    public ResponseEntity<QueueEntryResponse> callNext(
            @Valid @RequestBody CallNextRequest request) {
        Long staffId = SecurityUtils.requireStaffId();
        return ResponseEntity.ok(queueService.callNext(
                request.departmentId(), request.entryId(), staffId));
    }

    @PatchMapping("/entries/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK')")
    public ResponseEntity<QueueEntryResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateQueueStatusRequest request) {
        return ResponseEntity.ok(queueService.updateStatus(id, request.status()));
    }
}
