package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Live queue entry (BACKEND_SPEC.md §5.4). patientName is a free-text
 * snapshot (the frontend has no patientId on queue entries — §5.4 gap);
 * clinician/room are assigned server-side on call-next (Phase 5).
 */
@Entity
@Table(name = "queue_entries")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-facing ticket, e.g. "A-014". Generation is Phase-5-hardened (atomic). */
    @Column(nullable = false)
    private String ticketNumber;

    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false)
    private String departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QueueStatus status = QueueStatus.WAITING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QueuePriority priority = QueuePriority.ROUTINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatientSource source;

    @Column(nullable = false)
    private LocalDateTime checkInAt;

    private LocalDateTime calledAt;

    private String clinician;

    private String room;

    public QueueEntry() {}

    public QueueEntry(String ticketNumber, String patientName, String departmentId,
                      QueuePriority priority, PatientSource source, LocalDateTime checkInAt) {
        this.ticketNumber = ticketNumber;
        this.patientName = patientName;
        this.departmentId = departmentId;
        this.priority = priority;
        this.source = source;
        this.checkInAt = checkInAt;
    }

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public QueueStatus getStatus() { return status; }
    public void setStatus(QueueStatus status) { this.status = status; }

    public QueuePriority getPriority() { return priority; }
    public void setPriority(QueuePriority priority) { this.priority = priority; }

    public PatientSource getSource() { return source; }
    public void setSource(PatientSource source) { this.source = source; }

    public LocalDateTime getCheckInAt() { return checkInAt; }
    public void setCheckInAt(LocalDateTime checkInAt) { this.checkInAt = checkInAt; }

    public LocalDateTime getCalledAt() { return calledAt; }
    public void setCalledAt(LocalDateTime calledAt) { this.calledAt = calledAt; }

    public String getClinician() { return clinician; }
    public void setClinician(String clinician) { this.clinician = clinician; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
}
