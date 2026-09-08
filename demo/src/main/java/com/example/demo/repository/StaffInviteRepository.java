package com.example.demo.repository;

import com.example.demo.model.StaffInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffInviteRepository extends JpaRepository<StaffInvite, Long> {
    List<StaffInvite> findByFacilityIdAndStatusOrderByInvitedAtDesc(Long facilityId, String status);
    Optional<StaffInvite> findByPublicIdAndStatus(String publicId, String status);
    Optional<StaffInvite> findFirstByFacilityIdAndEmailAndStatus(Long facilityId, String email, String status);
}
