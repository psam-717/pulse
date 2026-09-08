package com.example.demo.controller;

import com.example.demo.dto.PatientNotificationResponse;
import com.example.demo.service.PatientNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Patient notification feed (booking approved/cancelled). Patient JWT only.
 */
@RestController
@RequestMapping("/api/patients/me/notifications")
@PreAuthorize("hasRole('PATIENT')")
public class PatientNotificationController {

    private final PatientNotificationService notificationService;

    public PatientNotificationController(PatientNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private static Long currentPatientId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }

    @GetMapping
    public ResponseEntity<List<PatientNotificationResponse>> list() {
        return ResponseEntity.ok(notificationService.listFor(currentPatientId()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<java.util.Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(java.util.Map.of("count",
                notificationService.unreadCount(currentPatientId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<List<PatientNotificationResponse>> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markRead(currentPatientId(), id));
    }

    @PostMapping("/read-all")
    public ResponseEntity<List<PatientNotificationResponse>> markAllRead() {
        return ResponseEntity.ok(notificationService.markAllRead(currentPatientId()));
    }
}
