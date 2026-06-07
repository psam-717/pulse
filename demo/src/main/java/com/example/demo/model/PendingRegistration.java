package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_registrations")
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String phone;

    private String ghanaCard;

    @Column(nullable = false)
    private String hashedPassword;

    @Column(nullable = false)
    private String otp;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public PendingRegistration() {}

    public PendingRegistration(String fullName, String phone, String ghanaCard,
                               String hashedPassword, String otp, LocalDateTime expiresAt) {
        this.fullName = fullName;
        this.phone = phone;
        this.ghanaCard = ghanaCard;
        this.hashedPassword = hashedPassword;
        this.otp = otp;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGhanaCard() { return ghanaCard; }
    public void setGhanaCard(String ghanaCard) { this.ghanaCard = ghanaCard; }

    public String getHashedPassword() { return hashedPassword; }
    public void setHashedPassword(String hashedPassword) { this.hashedPassword = hashedPassword; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}