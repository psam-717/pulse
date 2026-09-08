package com.example.demo.repository;

import com.example.demo.model.StaffSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffSessionRepository extends JpaRepository<StaffSession, String> {

    List<StaffSession> findTop50ByStaffIdOrderByLastActiveDesc(Long staffId);

    Optional<StaffSession> findByStaffIdAndId(Long staffId, String id);
}
