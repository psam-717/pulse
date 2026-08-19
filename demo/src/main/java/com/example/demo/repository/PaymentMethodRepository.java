package com.example.demo.repository;

import com.example.demo.model.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByPatientIdOrderByIdAsc(Long patientId);

    Optional<PaymentMethod> findByIdAndPatientId(Long id, Long patientId);

    long countByPatientId(Long patientId);
}
