package com.example.demo.repository;

import com.example.demo.model.Booking;
import com.example.demo.model.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByPatientId(Long patientId);
    Page<Booking> findByPatientId(Long patientId, Pageable pageable);
    Page<Booking> findByDoctorId(Long doctorId, Pageable pageable);
    boolean existsByPatientIdAndTimeSlotId(Long patientId, Long timeSlotId);

    boolean existsByTimeSlotId(Long timeSlotId);

    java.util.List<Booking> findByIdInAndPatient_Id(java.util.Collection<Long> ids, Long patientId);

    // Facility-plane: bookings for a department within a window (drives
    // Department.appointmentsToday — BACKEND_SPEC §5.3 [server-only])
    List<Booking> findByDepartmentIdAndBookingDateBetween(
            Long departmentId, LocalDateTime start, LocalDateTime end);

    // Appointment-plane queries — the appointment date is the time slot's
    // date (BACKEND_SPEC §6.1: GET /appointments dispatches on which of
    // date / from+to is present)
    List<Booking> findByTimeSlot_Date(LocalDate date);

    List<Booking> findByTimeSlot_DateAndDepartmentId(LocalDate date, Long departmentId);

    List<Booking> findByTimeSlot_DateBetween(LocalDate from, LocalDate to);

    long countByDoctorIdAndTimeSlot_DateAndStatusNot(Long doctorId, LocalDate date, BookingStatus status);

    @Query("""
            SELECT b FROM Booking b
            WHERE b.patient.id = :patientId
              AND b.paymentStatus <> com.example.demo.model.PaymentStatus.PAID
              AND b.status <> com.example.demo.model.BookingStatus.CANCELLED
              AND (b.checkedIn IS NULL OR b.checkedIn = false)
            ORDER BY b.payByDeadline ASC NULLS LAST
            """)
    List<Booking> findOutstandingForPatient(@Param("patientId") Long patientId);

    @Query("""
            SELECT b FROM Booking b
            WHERE b.payByDeadline IS NOT NULL
              AND b.payByDeadline <= :now
              AND b.paymentStatus <> com.example.demo.model.PaymentStatus.PAID
              AND b.status <> com.example.demo.model.BookingStatus.CANCELLED
              AND (b.checkedIn IS NULL OR b.checkedIn = false)
            """)
    List<Booking> findExpiredUnpaid(@Param("now") LocalDateTime now);
}
