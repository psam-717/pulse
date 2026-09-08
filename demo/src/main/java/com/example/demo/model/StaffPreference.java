package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Per-staff preferences (Phase 4, BACKEND_SPEC §6.7 rows 13-14).
 */
@Entity
@Table(name = "staff_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uq_staff_prefs_staff", columnNames = "staff_id"))
public class StaffPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(nullable = false, length = 16)
    private String language = "en-US";

    @Column(nullable = false, length = 64)
    private String timezone = "Africa/Accra";

    @Column(name = "date_locale", nullable = false, length = 16)
    private String dateLocale = "en-US";

    public StaffPreference() {
    }

    public StaffPreference(Long staffId) {
        this.staffId = staffId;
    }

    public Long getId() { return id; }
    public Long getStaffId() { return staffId; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getDateLocale() { return dateLocale; }
    public void setDateLocale(String dateLocale) { this.dateLocale = dateLocale; }
}
