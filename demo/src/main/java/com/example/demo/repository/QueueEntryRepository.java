package com.example.demo.repository;

import com.example.demo.model.QueueEntry;
import com.example.demo.model.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    List<QueueEntry> findByDepartmentId(String departmentId);

    /** Full waiting/active list for the facility's queue views (Phase 5 expands). */
    List<QueueEntry> findByDepartmentIdAndStatusIn(String departmentId, List<QueueStatus> statuses);

    List<QueueEntry> findByDepartmentIdAndStatus(String departmentId, QueueStatus status);

    long countByDepartmentIdAndStatus(String departmentId, QueueStatus status);

    /** Ticket number generation: entries for a department since start of day. */
    long countByDepartmentIdAndCheckInAtAfter(String departmentId, java.time.LocalDateTime after);

    /**
     * Highest numeric ticket sequence ever issued for a prefix (e.g. "C-004" → 4).
     * Used for collision-proof ticket generation: next ticket = max + 1, so tickets
     * never collide with historical/seed entries (the old "count today + 1" scheme did).
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(ticket_number FROM POSITION('-' IN ticket_number) + 1) AS INTEGER)), 0) "
            + "FROM queue_entries WHERE ticket_number LIKE :prefix || '-%'", nativeQuery = true)
    long maxTicketSequenceForPrefix(@Param("prefix") String prefix);

    long countByTicketNumber(String ticketNumber);

    // Undo-check-in support (checked_in → confirmed, §7.1): remove the
    // queue entries the hand-off created for this patient/department.
    java.util.List<QueueEntry> findByDepartmentIdAndPatientNameAndSource(
            String departmentId, String patientName, com.example.demo.model.PatientSource source);

    java.util.List<QueueEntry> findByDepartmentIdAndPatientNameAndSourceAndStatus(
            String departmentId, String patientName, com.example.demo.model.PatientSource source,
            QueueStatus status);

    /** Active queue lookup for the patient directory (name-based, demo-grade). */
    java.util.List<QueueEntry> findByPatientNameIgnoreCase(String patientName);

    java.util.Optional<QueueEntry> findByTicketNumber(String ticketNumber);

    java.util.List<QueueEntry> findByPatientIdAndStatusIn(Long patientId, List<QueueStatus> statuses);

    java.util.Optional<QueueEntry> findByBookingId(Long bookingId);
}
