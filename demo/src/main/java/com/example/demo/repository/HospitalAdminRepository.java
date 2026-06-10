package com.example.demo.repository;

import com.example.demo.model.HospitalAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HospitalAdminRepository extends JpaRepository<HospitalAdmin, Long> {

    Optional<HospitalAdmin> findByEmail(String email);

    Optional<HospitalAdmin> findByHospitalIdAndEmail(Long hospitalId, String email);
}