package com.example.demo.service;

import com.example.demo.dto.NotificationResponse;
import com.example.demo.model.Notification;
import com.example.demo.model.StaffMember;
import com.example.demo.model.StaffRole;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.StaffMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * In-app notification feed for facility staff (Phase 3, BACKEND_SPEC.md
 * §5.7/§6.6). Reads and mutations are always owner-scoped to the JWT staff
 * member — a staff member can never see another facility member's feed.
 *
 * type must be one of the web contract values: queue | no_show | appointment
 * | staff | summary | system.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StaffMemberRepository staffMemberRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               StaffMemberRepository staffMemberRepository) {
        this.notificationRepository = notificationRepository;
        this.staffMemberRepository = staffMemberRepository;
    }

    @Transactional
    public NotificationResponse create(Long staffId, Long facilityId, String type,
                                       String title, String body, String link) {
        Notification n = notificationRepository.save(
                new Notification(staffId, facilityId, type, title, body, link));
        return NotificationResponse.from(n);
    }

    /** Fan out to the facility's front desk + admins (queue lifecycle events). */
    @Transactional
    public void notifyQueueStaff(Long facilityId, String type, String title,
                                 String body, String link) {
        if (facilityId == null) return;
        staffMemberRepository.findByFacilityId(facilityId).stream()
                .filter(s -> s.getRole() == StaffRole.FRONT_DESK
                        || s.getRole() == StaffRole.ADMIN)
                .forEach(s -> notificationRepository.save(
                        new Notification(s.getId(), facilityId, type, title, body, link)));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listFor(Long staffId) {
        return notificationRepository.findTop100ByStaffIdOrderByCreatedAtDesc(staffId)
                .stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long staffId) {
        return notificationRepository.countByStaffIdAndReadFalse(staffId);
    }

    @Transactional
    public List<NotificationResponse> markRead(Long staffId, Long notificationId) {
        Notification n = notificationRepository.findByIdAndStaffId(notificationId, staffId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        n.setRead(true);
        notificationRepository.save(n);
        return listFor(staffId);
    }

    @Transactional
    public List<NotificationResponse> markAllRead(Long staffId) {
        notificationRepository.findTop100ByStaffIdOrderByCreatedAtDesc(staffId)
                .forEach(n -> {
                    if (!n.isRead()) n.setRead(true);
                });
        return listFor(staffId);
    }

    /** Demo seeding helper. */
    @Transactional
    public long countFor(Long staffId) {
        return notificationRepository.countByStaffId(staffId);
    }
}
