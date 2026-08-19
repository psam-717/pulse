package com.example.demo.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/** Hospital-authored visit note. Patients read only. */
@Entity
@Table(name = "visit_records")
public class VisitRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private String publicId;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private String hospital;

    @Column(nullable = false)
    private LocalDate visitDate;

    @Column(nullable = false)
    private String doctor;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    public VisitRecord() {}

    public Long getId() { return id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getHospital() { return hospital; }
    public void setHospital(String hospital) { this.hospital = hospital; }

    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }

    public String getDoctor() { return doctor; }
    public void setDoctor(String doctor) { this.doctor = doctor; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
