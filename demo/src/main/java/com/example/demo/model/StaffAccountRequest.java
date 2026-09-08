package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Pending account request (deactivate/delete) for the danger zone
 * (BACKEND_SPEC §6.7 rows 15-16). Records intent; resolution is out of scope.
 */
@Entity
@Table(name = "staff_account_requests",
        uniqueConstraints = @UniqueConstraint(name = "uq_staff_acct_req_staff", columnNames = "staff_id"),
        indexes = @Index(name = "idx_staff_acct_req_status", columnList = "status"))
public class StaffAccountRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(name = "transfer_ownership_to", length = 120)
    private String transferOwnershipTo;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(nullable = false, length = 16)
    private String status = "pending";

    public StaffAccountRequest() {
    }

    public StaffAccountRequest(Long staffId, String type, String transferOwnershipTo) {
        this.staffId = staffId;
        this.type = type;
        this.transferOwnershipTo = transferOwnershipTo;
    }

    public Long getId() { return id; }
    public Long getStaffId() { return staffId; }
    public String getType() { return type; }
    public String getTransferOwnershipTo() { return transferOwnershipTo; }
    public Instant getRequestedAt() { return requestedAt; }
    public String getStatus() { return status; }
}
