package com.example.demo.repository;

import com.example.demo.model.StaffPasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffPasswordResetOtpRepository extends JpaRepository<StaffPasswordResetOtp, Long> {

    /** The most recent code issued for this email (re-issue replaces the old one). */
    Optional<StaffPasswordResetOtp> findFirstByEmailOrderByCreatedAtDesc(String email);

    /** The most recent row carrying this resetToken for the email (confirm step). */
    Optional<StaffPasswordResetOtp> findFirstByEmailAndResetTokenOrderByCreatedAtDesc(String email, String resetToken);

    void deleteByEmail(String email);
}
