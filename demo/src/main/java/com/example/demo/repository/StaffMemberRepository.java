package com.example.demo.repository;

import com.example.demo.model.StaffAccountStatus;
import com.example.demo.model.StaffDutyStatus;
import com.example.demo.model.StaffMember;
import com.example.demo.model.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {

    Optional<StaffMember> findByEmail(String email);

    boolean existsByEmail(String email);

    // Facility-plane (web dashboard) — tenant-scoped queries
    List<StaffMember> findByFacilityId(Long facilityId);

    Optional<StaffMember> findByIdAndFacilityId(Long id, Long facilityId);

    boolean existsByEmailAndFacilityId(String email, Long facilityId);

    long countByRoleAndDepartmentIdAndAccountStatusAndFacilityId(
            StaffRole role, String departmentId, StaffAccountStatus accountStatus, Long facilityId);

    long countByRoleAndDepartmentIdAndAccountStatusAndDutyStatusAndFacilityId(
            StaffRole role, String departmentId, StaffAccountStatus accountStatus,
            StaffDutyStatus dutyStatus, Long facilityId);
}
