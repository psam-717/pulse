package com.example.demo.repository;

import com.example.demo.model.PatientNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientNotificationRepository extends JpaRepository<PatientNotification, Long> {
    List<PatientNotification> findTop100ByPatientIdOrderByCreatedAtDesc(Long patientId);
    long countByPatientIdAndReadFalse(Long patientId);
    Optional<PatientNotification> findByIdAndPatientId(Long id, Long patientId);
    long countByPatientId(Long patientId);
}
