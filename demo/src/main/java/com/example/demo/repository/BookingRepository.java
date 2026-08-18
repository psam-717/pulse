package com.example.demo.repository;

import com.example.demo.model.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
