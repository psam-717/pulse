package com.example.demo.repository;

import com.example.demo.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop100ByStaffIdOrderByCreatedAtDesc(Long staffId);

    long countByStaffIdAndReadFalse(Long staffId);

    Optional<Notification> findByIdAndStaffId(Long id, Long staffId);

    long countByStaffId(Long staffId);
}
