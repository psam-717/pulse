package com.example.demo.repository;

import com.example.demo.model.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findByDepartmentId(Long departmentId);
    Page<Doctor> findByDepartmentId(Long departmentId, Pageable pageable);
    Optional<Doctor> findByWorkspaceId(String workspaceId);
    Optional<Doctor> findByWorkspaceIdAndEmail(String workspaceId, String email);
    long countByDepartmentId(Long departmentId);

    @Query("SELECT d.workspaceId FROM Doctor d WHERE d.department.abbreviation = :abbrev ORDER BY d.workspaceId DESC LIMIT 1")
    Optional<String> findLastWorkspaceIdByDepartmentAbbreviation(@Param("abbrev") String abbreviation);
}