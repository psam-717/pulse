package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Facility-plane staff login session (Phase 4, BACKEND_SPEC §6.7 rows 8-10).
 * id = the JWT "sid" claim; revoking = deleting the row (JwtAuthFilter then
 * rejects tokens whose sid has no live session row).
 */
@Entity
@Table(name = "staff_sessions", indexes = {
        @Index(name = "idx_staff_sessions_staff_active", columnList = "staff_id, last_active")
})
public class StaffSession {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(nullable = false, length = 60)
    private String device = "Unknown device";

    @Column(nullable = false, length = 60)
    private String browser = "Browser";

    @Column(nullable = false, length = 80)
    private String location = "Remote";

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "last_active", nullable = false)
    private Instant lastActive = Instant.now();

    public StaffSession() {
    }

    public StaffSession(String id, Long staffId, String device, String browser,
                        String location, String userAgent) {
        this.id = id;
        this.staffId = staffId;
        this.device = device;
        this.browser = browser;
        this.location = location;
        this.userAgent = userAgent;
    }

    public String getId() { return id; }
    public Long getStaffId() { return staffId; }
    public String getDevice() { return device; }
    public String getBrowser() { return browser; }
    public String getLocation() { return location; }
    public String getUserAgent() { return userAgent; }
    public Instant getLastActive() { return lastActive; }
    public void setLastActive(Instant lastActive) { this.lastActive = lastActive; }
}
