package com.example.demo.repository;

import com.example.demo.model.LoginOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginOtpRepository extends JpaRepository<LoginOtp, Long> {

    /** The most recent code issued for this email (login re-issues on every attempt). */
    Optional<LoginOtp> findFirstByEmailOrderByCreatedAtDesc(String email);

    void deleteByEmail(String email);
}
