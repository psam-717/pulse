package com.example.demo.repository;

import com.example.demo.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    List<TimeSlot> findByDoctorIdAndDateAndIsBooked(Long doctorId, LocalDate date, boolean isBooked);
}
