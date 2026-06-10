package com.example.demo.service;

import com.example.demo.config.JwtUtil;
import com.example.demo.dto.*;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HospitalService {

    private static final Logger log = LoggerFactory.getLogger(HospitalService.class);

    private final HospitalRepository hospitalRepository;
    private final HospitalAdminRepository adminRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    public HospitalService(HospitalRepository hospitalRepository,
                           HospitalAdminRepository adminRepository,
                           DepartmentRepository departmentRepository,
                           DoctorRepository doctorRepository,
                           WorkingHoursRepository workingHoursRepository,
                           JwtUtil jwtUtil) {
        this.hospitalRepository = hospitalRepository;
        this.adminRepository = adminRepository;
        this.departmentRepository = departmentRepository;
        this.doctorRepository = doctorRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    // ===== REGISTRATION & AUTH =====

    @Transactional
    public RegistrationResponse register(HospitalRequest request) {
        // Validate uniqueness
        if (hospitalRepository.findByLicenseNumber(request.licenseNumber()).isPresent()) {
            throw new IllegalArgumentException("A hospital with this license number is already registered");
        }
        if (hospitalRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("A hospital with this email is already registered");
        }
        if (adminRepository.findByEmail(request.adminEmail()).isPresent()) {
            throw new IllegalArgumentException("An admin with this email already exists");
        }

        // Create Hospital
        Hospital hospital = new Hospital(
                request.name(),
                request.licenseNumber(),
                request.address(),
                request.phone(),
                request.email()
        );
        hospital.setLicenseDocumentUrl(request.licenseDocumentUrl());
        hospital.setLatitude(request.latitude());
        hospital.setLongitude(request.longitude());
        hospital.setSpecialties(request.specialties());
        hospital.setCapacity(request.capacity());
        hospital = hospitalRepository.save(hospital);

        // Create PRIMARY_ADMIN
        String hashedPassword = passwordEncoder.encode(request.adminPassword());
        HospitalAdmin admin = new HospitalAdmin(
                hospital,
                request.adminFullName(),
                request.adminEmail(),
                hashedPassword,
                request.adminPhone(),
                AdminRole.PRIMARY_ADMIN
        );
        admin = adminRepository.save(admin);

        log.info("Hospital registered: {} (ID={}), admin: {} (ID={})",
                hospital.getName(), hospital.getId(), admin.getEmail(), admin.getId());

        String token = jwtUtil.generateAdminToken(
                admin.getId(), hospital.getId(), "HOSPITAL_ADMIN");

        return new RegistrationResponse(
                "success",
                "Hospital registered successfully. Awaiting license verification.",
                hospital.getId(), admin.getId(), token
        );
    }

    public AuthResponse login(HospitalLoginRequest request) {
        HospitalAdmin admin = adminRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        boolean isSuper = admin.getRole() == AdminRole.SUPER_ADMIN;
        String role = isSuper ? "SUPER_ADMIN" : "HOSPITAL_ADMIN";
        Long hospitalId = isSuper ? null : admin.getHospital().getId();

        String token = jwtUtil.generateAdminToken(admin.getId(), hospitalId, role);

        return new AuthResponse(token, role, admin.getId(), "Login successful");
    }

    // ===== LICENSE VERIFICATION (Super Admin) =====

    @Transactional
    public HospitalResponse verifyLicense(Long hospitalId, LicenseVerifyRequest request) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found"));

        VerificationStatus status;
        try {
            status = VerificationStatus.valueOf(request.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status. Must be APPROVED or REJECTED");
        }

        if (status == VerificationStatus.PENDING) {
            throw new IllegalArgumentException("Cannot set status back to PENDING");
        }

        hospital.setVerificationStatus(status);
        if (status == VerificationStatus.REJECTED && request.rejectionReason() != null) {
            hospital.setRejectionReason(request.rejectionReason());
        } else if (status == VerificationStatus.APPROVED) {
            hospital.setRejectionReason(null);   // Clear any previous rejection
        }

        hospital = hospitalRepository.save(hospital);

        log.info("Hospital {} (ID={}) verification: {}", hospital.getName(), hospitalId, status);
        return toResponse(hospital);
    }

    // ===== DEPARTMENT MANAGEMENT (Hospital Admin) =====

    @Transactional
    public Department createDepartment(Long hospitalId, DepartmentRequest request) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found"));

        log.debug("Creating department '{}' for hospital ID={}", request.name(), hospitalId);

        // Check for duplicate name within this hospital
        if (departmentRepository.findByNameAndHospitalId(request.name(), hospitalId).isPresent()) {
            throw new IllegalArgumentException(
                    "Department '" + request.name() + "' already exists in this hospital");
        }

        // Check for duplicate abbreviation within this hospital
        if (departmentRepository.findByAbbreviation(request.abbreviation()).isPresent()) {
            throw new IllegalArgumentException(
                    "Abbreviation '" + request.abbreviation() + "' is already in use");
        }

        Department parent = null;
        if (request.parentDepartmentId() != null) {
            parent = departmentRepository.findByIdAndHospitalId(
                    request.parentDepartmentId(), hospitalId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent department not found in this hospital"));
        }

        Department department = new Department(
                request.name(),
                request.abbreviation(),
                request.description(),
                request.consultationFee(),
                hospital
        );
        department.setParentDepartment(parent);

        return departmentRepository.save(department);
    }

    public List<Department> listDepartments(Long hospitalId) {
        return departmentRepository.findByHospitalId(hospitalId);
    }

    @Transactional
    public void deleteDepartment(Long hospitalId, Long departmentId) {
        Department dept = departmentRepository.findByIdAndHospitalId(departmentId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in this hospital"));

        long doctorCount = doctorRepository.countByDepartmentId(departmentId);
        if (doctorCount > 0) {
            throw new IllegalStateException(
                    "Cannot delete department with " + doctorCount + " active doctors. Reassign them first.");
        }

        departmentRepository.delete(dept);
    }

    // ===== WORKING HOURS MANAGEMENT (Hospital Admin) =====

    @Transactional
    public List<WorkingHours> configureWorkingHours(Long hospitalId, WorkingHoursRequest request) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found"));

        // Replace all existing hours
        workingHoursRepository.deleteByHospitalId(hospitalId);

        List<WorkingHours> hours = request.entries().stream()
                .map(e -> {
                    WorkingHours wh = new WorkingHours(hospital, e.dayOfWeek(), e.openTime(), e.closeTime());
                    wh.setClosed(e.isClosed());
                    return wh;
                })
                .toList();

        return workingHoursRepository.saveAll(hours);
    }

    public List<WorkingHours> getWorkingHours(Long hospitalId) {
        return workingHoursRepository.findByHospitalId(hospitalId);
    }

    // ===== HELPERS =====

    public Hospital getHospitalById(Long hospitalId) {
        return hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found"));
    }

    public HospitalResponse toResponse(Hospital h) {
        return new HospitalResponse(
                h.getId(), h.getName(), h.getLicenseNumber(),
                h.getLicenseDocumentUrl(), h.getAddress(),
                h.getLatitude(), h.getLongitude(),
                h.getSpecialties(), h.getCapacity(),
                h.getPhone(), h.getEmail(),
                h.getVerificationStatus().name(),
                h.getRejectionReason(),
                h.getCreatedAt() != null ? h.getCreatedAt().toString() : null
        );
    }
}