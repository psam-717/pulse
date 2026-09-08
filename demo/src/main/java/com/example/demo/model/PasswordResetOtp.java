package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One-time password-reset code for patients (forgot-password flow, BE-11).
 *
 * <p>Same convention as {@link LoginOtp}: single-use, expiring, attempt-limited,
 * code stored plain for dev (real delivery via SMS/email is a TODO — dev mode
 * logs it and echoes it in the response). Keyed by the patient's canonical
 * phone number so the code survives whichever identifier (phone, Ghana Card,
 * patient number) the user typed.
 */
@Entity
@Table(name = "password_reset_otps")
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Canonical phone of the patient the code belongs to. */
    @Column(nullable = false)
    private String phone;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private boolean used = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** High-entropy capability returned by the verify step (BE-11/FE #33). Null until verified. */
    @Column(length = 64)
    private String resetToken;

    /** When the resetToken expires (short TTL — the code already proved phone ownership). */
    private LocalDateTime tokenExpiresAt;

    /** True once the resetToken was consumed by a successful password change. Nullable so the
     *  columns can be added by ddl-auto to a table that already has rows. */
    private Boolean tokenUsed = false;

    public PasswordResetOtp() {}

    public PasswordResetOtp(String phone, String code, LocalDateTime expiresAt) {
        this.phone = phone;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }

    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(LocalDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public Boolean getTokenUsed() { return tokenUsed; }
    public void setTokenUsed(Boolean tokenUsed) { this.tokenUsed = tokenUsed; }
}
