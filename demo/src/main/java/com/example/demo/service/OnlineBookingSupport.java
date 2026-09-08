package com.example.demo.service;

import com.example.demo.model.Doctor;
import com.example.demo.model.StaffRole;
import com.example.demo.repository.StaffMemberRepository;

import java.util.List;
import java.util.Objects;

/**
 * Shared rule for online (mobile) bookings: a legacy Doctor row may take
 * bookings only when it maps to a real staff member of this facility with
 * role DOCTOR (linked by email). Without that link the booking could never
 * appear in any doctor's web workspace ("My Appointments" filters by the
 * stable staff↔legacy-doctor email link), orphaning the appointment.
 */
public final class OnlineBookingSupport {

    private OnlineBookingSupport() {
    }

    public static List<Doctor> staffLinkedDoctors(List<Doctor> doctors,
                                                  StaffMemberRepository staffRepository,
                                                  Long facilityId) {
        if (facilityId == null) return List.of();
        return doctors.stream()
                .filter(d -> d.getEmail() != null && !d.getEmail().isBlank())
                .filter(d -> d.getHospital() != null
                        && Objects.equals(d.getHospital().getId(), facilityId))
                .filter(d -> staffRepository.findByEmail(d.getEmail().trim().toLowerCase())
                        .map(s -> s.getRole() == StaffRole.DOCTOR
                                && Objects.equals(s.getFacilityId(), facilityId))
                        .orElse(false))
                .toList();
    }
}
