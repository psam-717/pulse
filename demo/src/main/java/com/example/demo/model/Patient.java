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

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
}