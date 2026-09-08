package com.example.demo.repository;

import com.example.demo.model.StaffAccountRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffAccountRequestRepository extends JpaRepository<StaffAccountRequest, Long> {
    Optional<StaffAccountRequest> findByStaffId(Long staffId);
    Optional<StaffAccountRequest> findByStaffIdAndStatus(Long staffId, String status);
}
