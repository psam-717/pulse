package com.example.demo.repository;

import com.example.demo.model.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findByHospitalId(Long hospitalId);

    void deleteByHospitalId(Long hospitalId);
}