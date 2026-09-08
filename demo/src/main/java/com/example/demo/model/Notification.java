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
 * In-app notification for a facility staff member (BACKEND_SPEC.md §5.7).
 * type values match the web contract exactly: queue | no_show | appointment
 * | staff | summary | system.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_staff_created", columnList = "staff_id, created_at"),
        @Index(name = "idx_notif_staff_read", columnList = "staff_id, is_read")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    /** Owning facility of the event (informational; reads are always owner-scoped). */
    @Column(name = "facility_id")
    private Long facilityId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    /** Optional in-app route to navigate on click (e.g. "/d/live-queue"). */
    @Column(length = 255)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Notification() {
    }

    public Notification(Long staffId, Long facilityId, String type, String title,
                        String body, String link) {
        this.staffId = staffId;
        this.facilityId = facilityId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
    }

    public Long getId() { return id; }

    public Long getStaffId() { return staffId; }

    public Long getFacilityId() { return facilityId; }

    public String getType() { return type; }

    public String getTitle() { return title; }

    public String getBody() { return body; }

    public String getLink() { return link; }

    public boolean isRead() { return read; }

    public void setRead(boolean read) { this.read = read; }

    public Instant getCreatedAt() { return createdAt; }
}
