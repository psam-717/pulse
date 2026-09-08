package com.example.demo.service;

import com.example.demo.dto.PatientNotificationResponse;
import com.example.demo.model.PatientNotification;
import com.example.demo.repository.PatientNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Patient in-app notification feed (booking lifecycle). Owner-scoped to the
 * patient JWT; created by staff actions (approve/cancel) server-side.
 */
@Service
public class PatientNotificationService {

    private final PatientNotificationRepository notificationRepository;

    public PatientNotificationService(PatientNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public PatientNotificationResponse create(Long patientId, String type, String title,
                                              String body, String link) {
        return PatientNotificationResponse.from(notificationRepository.save(
                new PatientNotification(patientId, type, title, body, link)));
    }

    @Transactional(readOnly = true)
    public List<PatientNotificationResponse> listFor(Long patientId) {
        return notificationRepository.findTop100ByPatientIdOrderByCreatedAtDesc(patientId)
                .stream().map(PatientNotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long patientId) {
        return notificationRepository.countByPatientIdAndReadFalse(patientId);
    }

    @Transactional
    public List<PatientNotificationResponse> markRead(Long patientId, Long id) {
        PatientNotification n = notificationRepository.findByIdAndPatientId(id, patientId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        n.setRead(true);
        notificationRepository.save(n);
        return listFor(patientId);
    }

    @Transactional
    public List<PatientNotificationResponse> markAllRead(Long patientId) {
        notificationRepository.findTop100ByPatientIdOrderByCreatedAtDesc(patientId)
                .forEach(n -> {
                    if (!n.isRead()) n.setRead(true);
                });
        return listFor(patientId);
    }

    @Transactional
    public long countFor(Long patientId) {
        return notificationRepository.countByPatientId(patientId);
    }
}
