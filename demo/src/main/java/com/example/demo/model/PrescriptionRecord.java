package com.example.demo.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/** Hospital-authored prescription. Patients read only. */
@Entity
@Table(name = "prescription_records")
public class PrescriptionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private String publicId;

    @Column(nullable = false)
    private String medication;

    @Column(nullable = false)
    private String dose;

    @Column(nullable = false)
    private String prescribingDoctor;

    @Column(nullable = false)
    private String hospital;

    @Column(nullable = false)
    private LocalDate prescribedDate;

    public PrescriptionRecord() {}

    public Long getId() { return id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }

    public String getMedication() { return medication; }
    public void setMedication(String medication) { this.medication = medication; }

    public String getDose() { return dose; }
    public void setDose(String dose) { this.dose = dose; }

    public String getPrescribingDoctor() { return prescribingDoctor; }
    public void setPrescribingDoctor(String prescribingDoctor) { this.prescribingDoctor = prescribingDoctor; }

    public String getHospital() { return hospital; }
    public void setHospital(String hospital) { this.hospital = hospital; }

    public LocalDate getPrescribedDate() { return prescribedDate; }
    public void setPrescribedDate(LocalDate prescribedDate) { this.prescribedDate = prescribedDate; }
}
