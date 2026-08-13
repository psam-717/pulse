package com.example.demo.repository;

import com.example.demo.model.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {

    Optional<StaffMember> findByEmail(String email);

    boolean existsByEmail(String email);
}
