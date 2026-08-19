package com.example.demo.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/** Hospital-authored lab result. Values stored as JSON. Patients read only. */
@Entity
@Table(name = "lab_result_records")
public class LabResultRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private String publicId;

    @Column(nullable = false)
    private String testName;

    @Column(nullable = false)
    private String hospital;

    @Column(nullable = false)
    private String orderingDoctor;

    @Column(nullable = false)
    private LocalDate resultDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String valuesJson;

    public LabResultRecord() {}

    public Long getId() { return id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }

    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }

    public String getHospital() { return hospital; }
    public void setHospital(String hospital) { this.hospital = hospital; }

    public String getOrderingDoctor() { return orderingDoctor; }
    public void setOrderingDoctor(String orderingDoctor) { this.orderingDoctor = orderingDoctor; }

    public LocalDate getResultDate() { return resultDate; }
    public void setResultDate(LocalDate resultDate) { this.resultDate = resultDate; }

    public String getValuesJson() { return valuesJson; }
    public void setValuesJson(String valuesJson) { this.valuesJson = valuesJson; }
}
