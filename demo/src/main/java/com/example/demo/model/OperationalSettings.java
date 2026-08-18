package com.example.demo.model;

import jakarta.persistence.*;

/**
 * Facility-plane operational settings (BACKEND_SPEC.md §5.8) — one row per
 * facility. queuePriorityLevels is persisted as a JSON array of
 * {id,label,weight} so admin-editable labels/weights survive restarts;
 * seeded from the QueuePriority enum defaults (Emergency 3, Urgent 2,
 * Routine 1) which mirror the frontend mock.
 */
@Entity
@Table(name = "operational_settings")
public class OperationalSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long facilityId;

    /** JSON array of {"id","label","weight"} — see QueuePriorityLevelDto. */
    @Column(columnDefinition = "TEXT")
    private String queuePriorityLevelsJson;

    private int queueRefreshSeconds = 10;
    private int appointmentSlotMinutes = 20;
    private int noShowGraceMinutes = 15;
    private boolean sendPatientEmailConfirmations = true;
    private boolean sendPatientSmsReminders = false;

    public OperationalSettings() {}

    public OperationalSettings(Long facilityId) {
        this.facilityId = facilityId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }

    public String getQueuePriorityLevelsJson() { return queuePriorityLevelsJson; }
    public void setQueuePriorityLevelsJson(String queuePriorityLevelsJson) { this.queuePriorityLevelsJson = queuePriorityLevelsJson; }

    public int getQueueRefreshSeconds() { return queueRefreshSeconds; }
    public void setQueueRefreshSeconds(int queueRefreshSeconds) { this.queueRefreshSeconds = queueRefreshSeconds; }

    public int getAppointmentSlotMinutes() { return appointmentSlotMinutes; }
    public void setAppointmentSlotMinutes(int appointmentSlotMinutes) { this.appointmentSlotMinutes = appointmentSlotMinutes; }

    public int getNoShowGraceMinutes() { return noShowGraceMinutes; }
    public void setNoShowGraceMinutes(int noShowGraceMinutes) { this.noShowGraceMinutes = noShowGraceMinutes; }

    public boolean isSendPatientEmailConfirmations() { return sendPatientEmailConfirmations; }
    public void setSendPatientEmailConfirmations(boolean sendPatientEmailConfirmations) { this.sendPatientEmailConfirmations = sendPatientEmailConfirmations; }

    public boolean isSendPatientSmsReminders() { return sendPatientSmsReminders; }
    public void setSendPatientSmsReminders(boolean sendPatientSmsReminders) { this.sendPatientSmsReminders = sendPatientSmsReminders; }
}
