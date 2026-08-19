package com.example.demo.repository;

import com.example.demo.model.VisitRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VisitRecordRepository extends JpaRepository<VisitRecord, Long> {
    List<VisitRecord> findByPatientIdOrderByVisitDateDesc(Long patientId);
    boolean existsByPatientIdAndPublicId(Long patientId, String publicId);
}
