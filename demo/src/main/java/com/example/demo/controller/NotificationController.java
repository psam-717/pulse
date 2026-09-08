package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.NotificationCountResponse;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * In-app notification feed (BACKEND_SPEC.md §6.6). All reads/mutations are
 * owner-scoped to the JWT staff member.
 */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list() {
        return ResponseEntity.ok(notificationService.listFor(SecurityUtils.requireStaffId()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<NotificationCountResponse> unreadCount() {
        return ResponseEntity.ok(
                new NotificationCountResponse(notificationService.unreadCount(SecurityUtils.requireStaffId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<List<NotificationResponse>> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(
                notificationService.markRead(SecurityUtils.requireStaffId(), id));
    }

    @PostMapping("/read-all")
    public ResponseEntity<List<NotificationResponse>> markAllRead() {
        return ResponseEntity.ok(notificationService.markAllRead(SecurityUtils.requireStaffId()));
    }
}
