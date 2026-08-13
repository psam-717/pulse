package com.example.demo.service;

import com.example.demo.dto.PatientResponse;
import com.example.demo.model.Department;
import com.example.demo.model.Patient;
import com.example.demo.model.QueueEntry;
import com.example.demo.model.QueueStatus;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.PatientRepository;
import com.example.demo.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Facility-plane patient directory (BACKEND_SPEC.md §5.5). Read side only
 * for now — mutations (register, clinical record updates) land with the
 * full Phase 4. Current-visit is derived live from the queue.
 */
@Service
public class PatientService {

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

    // ===== Helpers =====

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
