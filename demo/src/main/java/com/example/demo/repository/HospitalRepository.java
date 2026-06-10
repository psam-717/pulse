package com.example.demo.repository;

import com.example.demo.model.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    Optional<Hospital> findByLicenseNumber(String licenseNumber);

    Optional<Hospital> findByEmail(String email);

    @Query(value = """
            SELECT *, (
                6371 * acos(
                    cos(radians(?1)) * cos(radians(latitude)) *
                    cos(radians(longitude) - radians(?2)) +
                    sin(radians(?1)) * sin(radians(latitude))
                )
            ) AS distance
            FROM hospitals
            WHERE verification_status = 'APPROVED'
              AND latitude IS NOT NULL
              AND longitude IS NOT NULL
            HAVING distance < ?3
            ORDER BY distance
            """, nativeQuery = true)
    List<Hospital> findNearby(double lat, double lng, double radiusKm);

    List<Hospital> findByVerificationStatus(
            com.example.demo.model.VerificationStatus status);
}