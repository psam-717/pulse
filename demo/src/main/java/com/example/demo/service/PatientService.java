package com.example.demo.service;

import com.example.demo.dto.CreatePatientRequest;
import com.example.demo.dto.PatientResponse;
import com.example.demo.dto.RecordVitalsRequest;
import com.example.demo.dto.UpdateClinicalRecordRequest;
import com.example.demo.dto.UpdatePatientRequest;
import com.example.demo.model.Department;
import com.example.demo.model.Gender;
import com.example.demo.model.Patient;
import com.example.demo.model.QueueEntry;
import com.example.demo.model.QueueStatus;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.PatientRepository;
import com.example.demo.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Facility-plane patient directory (BACKEND_SPEC.md §5.5) — read + the
 * mutation endpoints the web dashboard uses (register, demographics update,
 * clinical record, vitals). Current-visit is derived live from the queue.
 */
@Service
public class PatientService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PatientRepository patientRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final DepartmentRepository departmentRepository;

    public PatientService(PatientRepository patientRepository,
                          QueueEntryRepository queueEntryRepository,
                          DepartmentRepository departmentRepository) {
        this.patientRepository = patientRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.departmentRepository = departmentRepository;
    }

    public List<PatientResponse> list() {
        return patientRepository.findAll().stream()
                .sorted((a, b) -> {
                    var ra = a.getRegisteredAt();
                    var rb = b.getRegisteredAt();
                    if (ra == null || rb == null) return 0;
                    return rb.compareTo(ra);
                })
                .map(this::toResponse)
                .toList();
    }

    public PatientResponse get(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        return toResponse(patient);
    }

    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        if (patientRepository.findByPhone(request.phone()).isPresent()) {
            throw new IllegalArgumentException(
                    "A patient with phone " + request.phone() + " is already registered.");
        }
        String[] name = splitName(request.name());
        Patient p = new Patient(name[0], name[1],
                LocalDate.parse(request.dateOfBirth()),
                mapGender(request.gender()),
                request.email(), request.phone(),
                request.address(),
                "GHA-REG-" + System.currentTimeMillis() % 1000000000L,
                "{no-password}");
        p.setPassword("{no-password}"); // web-registered; mobile login not enabled
        p.setBloodType(request.bloodType());
        p.setPatientNumber(nextPatientNumber());
        return toResponse(patientRepository.save(p));
    }

    @Transactional
    public PatientResponse update(Long id, UpdatePatientRequest request) {
        Patient p = patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        if (request.name() != null && !request.name().isBlank()) {
            String[] name = splitName(request.name());
            p.setFirstName(name[0]);
            p.setLastName(name[1]);
        }
        if (request.dateOfBirth() != null && !request.dateOfBirth().isBlank()) {
            p.setDateOfBirth(LocalDate.parse(request.dateOfBirth()));
        }
        if (request.gender() != null && !request.gender().isBlank()) {
            p.setGender(mapGender(request.gender()));
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            patientRepository.findByPhone(request.phone())
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException(
                                "A patient with phone " + request.phone() + " is already registered.");
                    });
            p.setPhone(request.phone());
        }
        if (request.email() != null && !request.email().isBlank()) {
            p.setEmail(request.email());
        }
        if (request.address() != null && !request.address().isBlank()) {
            p.setAddress(request.address());
        }
        if (request.bloodType() != null && !request.bloodType().isBlank()) {
            p.setBloodType(request.bloodType());
        }
        return toResponse(patientRepository.save(p));
    }

    @Transactional
    public PatientResponse updateClinicalRecord(Long id, UpdateClinicalRecordRequest request) {
        Patient p = patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        if (request.allergies() != null) {
            p.setAllergies(request.allergies().stream()
                    .map(String::trim).filter(a -> !a.isEmpty())
                    .collect(Collectors.joining(",")));
        }
        if (request.currentMedications() != null) {
            p.setCurrentMedications(toJson(request.currentMedications()));
        }
        return toResponse(patientRepository.save(p));
    }

    @Transactional
    public PatientResponse recordVitals(Long id, RecordVitalsRequest request) {
        Patient p = patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        String vitals = String.format(
                "{\"bloodPressure\":\"%s\",\"temperature\":\"%s\",\"pulse\":\"%s\",\"weight\":\"%s\",\"recordedAt\":\"%s\"}",
                escape(request.bloodPressure()), escape(request.temperature()),
                escape(request.pulse()), escape(request.weight()),
                LocalDateTime.now());
        p.setLatestVitals(vitals);
        return toResponse(patientRepository.save(p));
    }

    // ===== Helpers =====

    private String nextPatientNumber() {
        Optional<Patient> top = patientRepository
                .findTopByPatientNumberNotNullOrderByPatientNumberDesc();
        int next = top.map(p -> {
                    try {
                        return Integer.parseInt(p.getPatientNumber().replaceAll("\\D", "")) + 1;
                    } catch (NumberFormatException e) {
                        return 101;
                    }
                })
                .orElse(101);
        return String.format("PT-%05d", next);
    }

    private static String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space <= 0) return new String[]{trimmed, ""};
        return new String[]{trimmed.substring(0, space), trimmed.substring(space + 1)};
    }

    private static Gender mapGender(String g) {
        return switch (g.toLowerCase()) {
            case "female" -> Gender.FEMALE;
            case "male" -> Gender.MALE;
            default -> Gender.OTHER;
        };
    }

    private static String toJson(List<?> list) {
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private PatientResponse toResponse(Patient p) {
        QueueEntry active = findActiveQueueEntry(p.getFirstName() + " " + p.getLastName());
        String deptName = null;
        if (active != null) {
            deptName = departmentRepository.findById(
                            Long.parseLong(active.getDepartmentId()))
                    .map(Department::getName).orElse(null);
        }
        return PatientResponse.from(p, active, deptName);
    }

    /** Waiting/in-consultation queue entry for this patient, if any. */
    private QueueEntry findActiveQueueEntry(String patientName) {
        List<QueueEntry> candidates = queueEntryRepository
                .findByPatientNameIgnoreCase(patientName);
        return candidates.stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING
                        || e.getStatus() == QueueStatus.IN_CONSULTATION)
                .findFirst()
                .orElse(null);
    }
}
