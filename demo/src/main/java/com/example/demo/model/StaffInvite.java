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
 * Role invite for a facility (BACKEND_SPEC §6.7 rows 17-19). Cancel = status
 * "cancelled"; the API only surfaces pending invites.
 */
@Entity
@Table(name = "staff_invites",
        indexes = @Index(name = "idx_staff_invites_facility_status", columnList = "facility_id, status"))
public class StaffInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, unique = true)
    private String publicId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt = Instant.now();

    @Column(nullable = false, length = 16)
    private String status = "pending";

    public StaffInvite() {
    }

    public StaffInvite(String publicId, Long facilityId, String email, String role) {
        this.publicId = publicId;
        this.facilityId = facilityId;
        this.email = email.toLowerCase();
        this.role = role;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getFacilityId() { return facilityId; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public Instant getInvitedAt() { return invitedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
