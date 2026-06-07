package com.example.demo.service;

import com.example.demo.config.JwtUtil;
import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.PatientLoginRequest;
import com.example.demo.dto.SignupRequest;
import com.example.demo.dto.VerifyOtpRequest;
import com.example.demo.model.Patient;
import com.example.demo.model.PendingRegistration;
import com.example.demo.repository.PatientRepository;
import com.example.demo.repository.PendingRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int OTP_EXPIRY_MINUTES = 5;

    private final PendingRegistrationRepository pendingRepo;
    private final PatientRepository patientRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(PendingRegistrationRepository pendingRepo,
                       PatientRepository patientRepository,
                       JwtUtil jwtUtil) {
        this.pendingRepo = pendingRepo;
        this.patientRepository = patientRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public void initiateSignup(SignupRequest request) {
        String phone = request.phone();

        // Replace any existing pending registration for this number
        pendingRepo.findByPhone(phone).ifPresent(pendingRepo::delete);

        String otp = generateOtp();
        String hashedPassword = passwordEncoder.encode(request.password());
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        PendingRegistration pending = new PendingRegistration(
                request.fullName(), phone, request.ghanaCard(),
                hashedPassword, otp, expiresAt
        );
        pendingRepo.save(pending);

        // TODO: replace with real SMS provider (e.g. Twilio)
        log.info("OTP for {}: {}", phone, otp);
    }

    @Transactional
    public void verifyOtpAndCreatePatient(VerifyOtpRequest request) {
        PendingRegistration pending = pendingRepo.findByPhone(request.phone())
                .orElseThrow(() -> new IllegalArgumentException("No pending registration for this number"));

        if (LocalDateTime.now().isAfter(pending.getExpiresAt())) {
            pendingRepo.delete(pending);
            throw new IllegalStateException("OTP has expired. Please sign up again.");
        }

        if (!pending.getOtp().equals(request.otp())) {
            throw new IllegalArgumentException("Invalid OTP");
        }

        // OTP verified — create the patient account
        String[] nameParts = pending.getFullName().trim().split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        Patient patient = new Patient();
        patient.setFirstName(firstName);
        patient.setLastName(lastName);
        patient.setPhone(pending.getPhone());
        patient.setGhanaCard(pending.getGhanaCard());
        patient.setPassword(pending.getHashedPassword());
        patientRepository.save(patient);

        // Clean up the pending registration
        pendingRepo.delete(pending);
    }

    public AuthResponse patientLogin(PatientLoginRequest request) {
        String identifier = request.identifier();

        // Try to find by phone first, then ghanaCard
        Patient patient = patientRepository.findByPhone(identifier)
                .or(() -> patientRepository.findByGhanaCard(identifier))
                .orElseThrow(() -> new IllegalArgumentException("Patient not found with the provided identifier"));

        if (!passwordEncoder.matches(request.password(), patient.getPassword())) {
            throw new IllegalArgumentException("Invalid password");
        }

        String token = jwtUtil.generateToken(patient.getId(), "PATIENT");
        return new AuthResponse(token, "PATIENT", patient.getId(), "Login successful");
    }

    private String generateOtp() {
        return String.format("%06d", new Random().nextInt(999999));
    }
}