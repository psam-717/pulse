package com.example.demo.repository;

import com.example.demo.model.LabResultRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabResultRecordRepository extends JpaRepository<LabResultRecord, Long> {
    List<LabResultRecord> findByPatientIdOrderByResultDateDesc(Long patientId);
    boolean existsByPatientIdAndPublicId(Long patientId, String publicId);
}
