package com.example.demo.repository;

import com.example.demo.model.PrescriptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionRecordRepository extends JpaRepository<PrescriptionRecord, Long> {
    List<PrescriptionRecord> findByPatientIdOrderByPrescribedDateDesc(Long patientId);
    boolean existsByPatientIdAndPublicId(Long patientId, String publicId);
}
