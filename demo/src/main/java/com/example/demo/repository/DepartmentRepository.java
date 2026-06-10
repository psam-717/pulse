package com.example.demo.repository;

import com.example.demo.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByHospitalId(Long hospitalId);
    Optional<Department> findByIdAndHospitalId(Long id, Long hospitalId);
    Optional<Department> findByNameAndHospitalId(String name, Long hospitalId);
    Optional<Department> findByAbbreviation(String abbreviation);
}