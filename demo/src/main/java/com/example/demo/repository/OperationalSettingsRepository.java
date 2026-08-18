package com.example.demo.repository;

import com.example.demo.model.OperationalSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OperationalSettingsRepository extends JpaRepository<OperationalSettings, Long> {
    Optional<OperationalSettings> findByFacilityId(Long facilityId);
}
