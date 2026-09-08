package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * In-app notification for a patient (booking lifecycle: approved/cancelled).
 * Mirrors the staff Notification shape but is patient-scoped.
 */
@Entity
@Table(name = "patient_notifications", indexes = {
        @Index(name = "idx_patient_notif_created", columnList = "patient_id, created_at"),
        @Index(name = "idx_patient_notif_read", columnList = "patient_id, is_read")
})
public class PatientNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    /** Optional deep-link route on mobile (null = just open the feed). */
    @Column(length = 255)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PatientNotification() {
    }

    public PatientNotification(Long patientId, String type, String title,
                               String body, String link) {
        this.patientId = patientId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
    }

    public Long getId() { return id; }
    public Long getPatientId() { return patientId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public Instant getCreatedAt() { return createdAt; }
}
