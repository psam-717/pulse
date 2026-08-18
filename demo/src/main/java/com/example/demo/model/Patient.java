package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String email;

    @Column(unique = true)
    private String phone;

    @Column(unique = true)
    private String ghanaCard;

    @Column(nullable = false)
    private String password;

    private String address;

    // ===== Web dashboard / clinical record fields (BACKEND_SPEC §5.5) =====
    // Record-keeping only — Pulse never interprets clinical data.

    /** Human-facing number, e.g. "PT-00101". */
    @Column(unique = true)
    private String patientNumber;

    private String bloodType;

    /** Comma-separated allergies, e.g. "penicillin,aspirin". */
    @Column(columnDefinition = "TEXT")
    private String allergies;

    /** JSON array of {name, dose, frequency}. */
    @Column(columnDefinition = "TEXT")
    private String currentMedications;

    /** JSON object {bloodPressure, temperature, pulse, weight, recordedAt}. */
    @Column(columnDefinition = "TEXT")
    private String latestVitals;

    // ===== Mobile self-service medical profile (ARCHITECTURE.md §8 P1) =====
    // Structured, mobile-shaped fields. The flat web fields above are kept
    // in sync (both populated) so the web dashboard contract never breaks.

    @Column(columnDefinition = "TEXT")
    private String allergiesJson;      // JSON [{id,label,type}]

    @Column(columnDefinition = "TEXT")
    private String conditionsJson;     // JSON [{id,label}]

    @Column(columnDefinition = "TEXT")
    private String medicationsJson;    // JSON [{id,name,dose}]

    @Column(columnDefinition = "TEXT")
    private String vitalsJson;         // JSON [{id,date,systolic,...}] — history

    private String emergencyContactName;
    private String emergencyContactRelationship;
    private String emergencyContactPhone;

    // ===== Mobile insurance (ARCHITECTURE.md §8 P2 / G3) =====
    // Patient-scoped PII — Ghana DPA Act 843. Exposed as InsuranceDetails
    // (scheme, membershipNumber, cardholderName, expiryDate, cardPhotoUri).

    private String insuranceScheme;
    private String insuranceMembershipNumber;
    private String insuranceCardholderName;
    private LocalDate insuranceExpiryDate;
    private String insuranceCardPhotoUrl;

    private LocalDateTime registeredAt;

    @ManyToMany
    @JoinTable(
        name = "patient_doctors",
        joinColumns = @JoinColumn(name = "patient_id"),
        inverseJoinColumns = @JoinColumn(name = "doctor_id")
    )
    private List<Doctor> doctors = new ArrayList<>();

    public Patient() {}

    public Patient(String firstName, String lastName, LocalDate dateOfBirth,
                   Gender gender, String email, String phone, String address,
                   String ghanaCard, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.ghanaCard = ghanaCard;
        this.password = password;
        this.registeredAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGhanaCard() { return ghanaCard; }
    public void setGhanaCard(String ghanaCard) { this.ghanaCard = ghanaCard; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public List<Doctor> getDoctors() { return doctors; }
    public void setDoctors(List<Doctor> doctors) { this.doctors = doctors; }

    public String getPatientNumber() { return patientNumber; }
    public void setPatientNumber(String patientNumber) { this.patientNumber = patientNumber; }

    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }

    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }

    public String getCurrentMedications() { return currentMedications; }
    public void setCurrentMedications(String currentMedications) { this.currentMedications = currentMedications; }

    public String getLatestVitals() { return latestVitals; }
    public void setLatestVitals(String latestVitals) { this.latestVitals = latestVitals; }

    public String getAllergiesJson() { return allergiesJson; }
    public void setAllergiesJson(String allergiesJson) { this.allergiesJson = allergiesJson; }

    public String getConditionsJson() { return conditionsJson; }
    public void setConditionsJson(String conditionsJson) { this.conditionsJson = conditionsJson; }

    public String getMedicationsJson() { return medicationsJson; }
    public void setMedicationsJson(String medicationsJson) { this.medicationsJson = medicationsJson; }

    public String getVitalsJson() { return vitalsJson; }
    public void setVitalsJson(String vitalsJson) { this.vitalsJson = vitalsJson; }

    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }

    public String getEmergencyContactRelationship() { return emergencyContactRelationship; }
    public void setEmergencyContactRelationship(String emergencyContactRelationship) { this.emergencyContactRelationship = emergencyContactRelationship; }

    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }

    public String getInsuranceScheme() { return insuranceScheme; }
    public void setInsuranceScheme(String insuranceScheme) { this.insuranceScheme = insuranceScheme; }

    public String getInsuranceMembershipNumber() { return insuranceMembershipNumber; }
    public void setInsuranceMembershipNumber(String insuranceMembershipNumber) { this.insuranceMembershipNumber = insuranceMembershipNumber; }

    public String getInsuranceCardholderName() { return insuranceCardholderName; }
    public void setInsuranceCardholderName(String insuranceCardholderName) { this.insuranceCardholderName = insuranceCardholderName; }

    public LocalDate getInsuranceExpiryDate() { return insuranceExpiryDate; }
    public void setInsuranceExpiryDate(LocalDate insuranceExpiryDate) { this.insuranceExpiryDate = insuranceExpiryDate; }

    public String getInsuranceCardPhotoUrl() { return insuranceCardPhotoUrl; }
    public void setInsuranceCardPhotoUrl(String insuranceCardPhotoUrl) { this.insuranceCardPhotoUrl = insuranceCardPhotoUrl; }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
}