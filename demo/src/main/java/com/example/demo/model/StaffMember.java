package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

/**
 * Facility-plane user — the web dashboard's "staff" (BACKEND_SPEC.md §5.6).
 * One row per person per facility (one token, one facilityId — §2.3).
 *
 * Phase 1 scope: identity + auth fields sufficient for login and
 * {@code GET /auth/me}. Department linkage is denormalized (departmentId
 * as string matching the frontend contract); Phase 2 links it properly.
 */
@Entity
@Table(name = "staff_members")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StaffMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffRole role;

    @Column(nullable = false)
    private String title;

    private String specialty;

    /** "" when unassigned — matches frontend contract. */
    @Column(nullable = false)
    private String departmentId = "";

    @Column(nullable = false)
    private String departmentName = "";

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    /** "HH:MM"; overnight (end < start) is valid — see spec §7.6. */
    @Column(nullable = false)
    private String shiftStart;

    @Column(nullable = false)
    private String shiftEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffDutyStatus dutyStatus = StaffDutyStatus.ON_DUTY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffAccountStatus accountStatus = StaffAccountStatus.ACTIVE;

    private String avatarUrl;

    /** Tenant id — the facility this staff member belongs to (never client-settable). */
    @Column(nullable = false)
    private Long facilityId;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    public StaffMember() {}

    public StaffMember(String name, StaffRole role, String title, String specialty,
                       String departmentId, String departmentName, String email, String phone,
                       String shiftStart, String shiftEnd, StaffDutyStatus dutyStatus,
                       StaffAccountStatus accountStatus, String avatarUrl,
                       Long facilityId, String password) {
        this.name = name;
        this.role = role;
        this.title = title;
        this.specialty = specialty;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.email = email;
        this.phone = phone;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
        this.dutyStatus = dutyStatus;
        this.accountStatus = accountStatus;
        this.avatarUrl = avatarUrl;
        this.facilityId = facilityId;
        this.password = password;
    }

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public StaffRole getRole() { return role; }
    public void setRole(StaffRole role) { this.role = role; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getShiftStart() { return shiftStart; }
    public void setShiftStart(String shiftStart) { this.shiftStart = shiftStart; }

    public String getShiftEnd() { return shiftEnd; }
    public void setShiftEnd(String shiftEnd) { this.shiftEnd = shiftEnd; }

    public StaffDutyStatus getDutyStatus() { return dutyStatus; }
    public void setDutyStatus(StaffDutyStatus dutyStatus) { this.dutyStatus = dutyStatus; }

    public StaffAccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(StaffAccountStatus accountStatus) { this.accountStatus = accountStatus; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
