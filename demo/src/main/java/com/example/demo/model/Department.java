package com.example.demo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String abbreviation;

    private String description;

    @Column(nullable = false)
    private BigDecimal consultationFee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_department_id")
    private Department parentDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id", nullable = true)
    private Hospital hospital;

    // ===== Facility-plane fields (BACKEND_SPEC.md §5.3) =====
    // All nullable so ddl-auto=update can add them to existing tables;
    // the response DTO applies defaults for legacy rows.

    /** Tenant id — the facility this department belongs to (never client-settable). */
    private Long facilityId;

    /** active | closed | archived */
    private String status = "active";

    private String headDoctorName;

    private Integer rooms;

    /** "HH:MM" */
    private String opensAt = "08:00";

    /** "HH:MM" */
    private String closesAt = "17:00";

    private Boolean twentyFourSeven = false;

    public Department() {}

    public Department(String name, String abbreviation, String description,
                      BigDecimal consultationFee, Hospital hospital) {
        this.name = name;
        this.abbreviation = abbreviation;
        this.description = description;
        this.consultationFee = consultationFee;
        this.hospital = hospital;
    }

    // --- Getters & Setters ---

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAbbreviation() { return abbreviation; }
    public void setAbbreviation(String abbreviation) { this.abbreviation = abbreviation; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }

    public Department getParentDepartment() { return parentDepartment; }
    public void setParentDepartment(Department parentDepartment) { this.parentDepartment = parentDepartment; }

    public Hospital getHospital() { return hospital; }
    public void setHospital(Hospital hospital) { this.hospital = hospital; }

    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getHeadDoctorName() { return headDoctorName; }
    public void setHeadDoctorName(String headDoctorName) { this.headDoctorName = headDoctorName; }

    public Integer getRooms() { return rooms; }
    public void setRooms(Integer rooms) { this.rooms = rooms; }

    public String getOpensAt() { return opensAt; }
    public void setOpensAt(String opensAt) { this.opensAt = opensAt; }

    public String getClosesAt() { return closesAt; }
    public void setClosesAt(String closesAt) { this.closesAt = closesAt; }

    public Boolean getTwentyFourSeven() { return twentyFourSeven; }
    public void setTwentyFourSeven(Boolean twentyFourSeven) { this.twentyFourSeven = twentyFourSeven; }
}