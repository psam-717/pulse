package com.example.demo.repository;

import com.example.demo.model.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    /** The most recent code issued for this phone (re-issue replaces the old one). */
    Optional<PasswordResetOtp> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    void deleteByPhone(String phone);
}
