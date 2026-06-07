package com.example.demo.service;

import com.example.demo.config.JwtUtil;
import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.CreateDoctorRequest;
import com.example.demo.dto.DoctorLoginRequest;
import com.example.demo.model.Department;
import com.example.demo.model.Doctor;
import com.example.demo.model.Hospital;
import com.example.demo.repository.DoctorRepository;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.HospitalRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorAdminService {

    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final HospitalRepository hospitalRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public DoctorAdminService(DoctorRepository doctorRepository,
                              DepartmentRepository departmentRepository,
                              HospitalRepository hospitalRepository,
                              JwtUtil jwtUtil) {
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.hospitalRepository = hospitalRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public Doctor createDoctor(CreateDoctorRequest request) {
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));

        Hospital hospital = hospitalRepository.findById(request.hospitalId())
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found"));

        // Auto-generate workspace ID: {ABBREV}-DOC-{PADDED_NUMBER}
        String workspaceId = generateWorkspaceId(department.getAbbreviation());

        // Check uniqueness
        if (doctorRepository.findByWorkspaceId(workspaceId).isPresent()) {
            throw new IllegalStateException("Workspace ID collision: " + workspaceId);
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        Doctor doctor = new Doctor(
                request.firstName(), request.lastName(),
                request.specialization(), request.email(), request.phone(),
                request.licenseNumber(), workspaceId, hashedPassword,
                hospital, department
        );

        return doctorRepository.save(doctor);
    }

    public AuthResponse doctorLogin(DoctorLoginRequest request) {
        Doctor doctor = doctorRepository.findByWorkspaceIdAndEmail(request.workspaceId(), request.email())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No doctor found with workspace ID: " + request.workspaceId()));

        if (!passwordEncoder.matches(request.password(), doctor.getPassword())) {
            throw new IllegalArgumentException("Invalid password");
        }

        String token = jwtUtil.generateToken(doctor.getId(), "DOCTOR");
        return new AuthResponse(token, "DOCTOR", doctor.getId(), "Login successful");
    }

    private String generateWorkspaceId(String abbreviation) {
        // Find the last workspace ID for this department to determine next number
        String lastId = doctorRepository.findLastWorkspaceIdByDepartmentAbbreviation(abbreviation)
                .orElse(null);

        int nextNumber = 1;
        if (lastId != null) {
            // Extract the numeric suffix from e.g. "CARDIO-DOC-023"
            String[] parts = lastId.split("-");
            if (parts.length >= 3) {
                try {
                    nextNumber = Integer.parseInt(parts[parts.length - 1]) + 1;
                } catch (NumberFormatException ignored) {}
            }
        }

        return String.format("%s-DOC-%03d", abbreviation.toUpperCase(), nextNumber);
    }
}