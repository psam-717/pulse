package com.example.demo.service;

import com.example.demo.dto.QueueDepartmentResponse;
import com.example.demo.dto.QueueEntryResponse;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Department;
import com.example.demo.model.Patient;
import com.example.demo.model.PatientSource;
import com.example.demo.model.QueueEntry;
import com.example.demo.model.QueuePriority;
import com.example.demo.model.QueueStatus;
import com.example.demo.model.StaffMember;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.PatientRepository;
import com.example.demo.repository.QueueEntryRepository;
import com.example.demo.repository.StaffMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Live Queue read + call-next (BACKEND_SPEC.md §5.4, §6.4). Ticket
 * generation and call-next atomicity are deliberately simple here — the
 * Phase 5 pass hardens them (§7.3).
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final QueueEntryRepository queueEntryRepository;
    private final DepartmentRepository departmentRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final PatientRepository patientRepository;
    private final NotificationService notificationService;

    public QueueService(QueueEntryRepository queueEntryRepository,
                        DepartmentRepository departmentRepository,
                        StaffMemberRepository staffMemberRepository,
                        PatientRepository patientRepository,
                        NotificationService notificationService) {
        this.queueEntryRepository = queueEntryRepository;
        this.departmentRepository = departmentRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.patientRepository = patientRepository;
        this.notificationService = notificationService;
    }

    /** Per-department summaries for the sidebar / dept tabs. */
    public List<QueueDepartmentResponse> departments(Long facilityId) {
        List<QueueEntry> all = queueEntryRepository.findAll();
        return departmentRepository.findByFacilityId(facilityId).stream()
                .map(d -> summarize(d, all))
                .toList();
    }

    /** Entries for one department, or the whole facility when departmentId is
     *  omitted ("All" tab on the dashboard board). Department ids are always
     *  validated against the caller's facility so staff can never read another
     *  tenant's queue (BACKEND_SPEC §2.2). Waiting first, then by check-in time. */
    public List<QueueEntryResponse> entries(Long facilityId, String departmentId) {
        List<String> departmentIds;
        if (departmentId == null || departmentId.isBlank()) {
            departmentIds = departmentRepository.findByFacilityId(facilityId).stream()
                    .map(d -> String.valueOf(d.getId()))
                    .toList();
            if (departmentIds.isEmpty()) {
                return List.of();
            }
        } else {
            Department department;
            try {
                department = departmentRepository.findById(Long.valueOf(departmentId))
                        .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
            } catch (NumberFormatException e) {
                throw new ResourceNotFoundException("Department not found");
            }
            if (!java.util.Objects.equals(department.getFacilityId(), facilityId)) {
                throw new ResourceNotFoundException("Department not found");
            }
            departmentIds = List.of(departmentId);
        }
        return queueEntryRepository.findByDepartmentIdIn(departmentIds).stream()
                .sorted(Comparator
                        .comparing((QueueEntry e) -> e.getStatus() == QueueStatus.WAITING ? 0 : 1)
                        .thenComparing(QueueEntry::getCheckInAt))
                .map(QueueEntryResponse::from)
                .toList();
    }

    /** Move the next (or a specific) waiting entry into consultation. */
    @Transactional
    public QueueEntryResponse callNext(String departmentId, String entryId, Long staffId) {
        // Atomic pick (§7.3): the repository locks the candidate row with
        // PESSIMISTIC_WRITE, so two concurrent call-next requests cannot both
        // claim the same waiting patient — the loser's locked re-read sees
        // status already IN_CONSULTATION and falls through to the Conflict.
        QueueEntry target;
        if (entryId != null && !entryId.isBlank()) {
            target = queueEntryRepository.findByIdAndStatus(Long.valueOf(entryId), QueueStatus.WAITING)
                    .orElseThrow(() -> new ConflictException(
                            "That ticket is no longer waiting in this queue."));
        } else {
            target = queueEntryRepository
                    .findFirstByDepartmentIdAndStatusOrderByCheckInAtAsc(departmentId, QueueStatus.WAITING)
                    .orElseThrow(() -> new ConflictException("No patients waiting in this queue."));
        }

        target.setStatus(QueueStatus.IN_CONSULTATION);
        target.setCalledAt(LocalDateTime.now());
        if (target.getClinician() == null) {
            staffMemberRepository.findById(staffId)
                    .map(StaffMember::getName)
                    .ifPresent(target::setClinician);
        }
        if (target.getClinicianId() == null) {
            target.setClinicianId(staffId);
        }
        queueEntryRepository.save(target);
        // Phase 3: front desk + admins of the department's facility get a
        // "now serving" ping so they can update boards / catch no-shows.
        try {
            Department d = departmentRepository.findById(Long.valueOf(departmentId)).orElse(null);
            if (d != null) {
                notificationService.notifyQueueStaff(
                        d.getFacilityId(), "queue",
                        "Now serving " + target.getTicketNumber(),
                        (target.getPatientName() != null ? target.getPatientName() + " · " : "")
                                + d.getName()
                                + (target.getClinician() != null ? " · " + target.getClinician() : ""),
                        "/d/live-queue");
            }
        } catch (NumberFormatException ignored) {
            // Unknown department id → skip the notification, never fail call-next.
        }
        return QueueEntryResponse.from(target);
    }

    /** Front-desk walk-in registration (§10.1): existing patient → WAITING entry. */
    @Transactional
    public QueueEntryResponse createWalkIn(Long facilityId, Long patientId,
                                           String departmentId, String priorityRaw) {
        Department department;
        try {
            department = departmentRepository.findById(Long.valueOf(departmentId))
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("Department not found");
        }
        if (!java.util.Objects.equals(department.getFacilityId(), facilityId)) {
            throw new ResourceNotFoundException("Department not found");
        }
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        if (!queueEntryRepository.findByPatientIdAndStatusIn(patient.getId(),
                List.of(QueueStatus.WAITING, QueueStatus.IN_CONSULTATION)).isEmpty()) {
            throw new ConflictException("Patient already has an active queue ticket");
        }

        QueuePriority priority = QueuePriority.ROUTINE;
        if (priorityRaw != null && !priorityRaw.isBlank()) {
            try {
                priority = QueuePriority.valueOf(priorityRaw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("priority must be routine, urgent or emergency");
            }
        }

        String code = department.getAbbreviation();
        String prefix = (code != null && !code.isBlank())
                ? code.substring(0, 1).toUpperCase(java.util.Locale.ROOT) : "D";
        long nextSeq = queueEntryRepository.maxTicketSequenceForPrefix(prefix) + 1;
        String ticket = prefix + "-" + String.format("%03d", nextSeq);
        String name = (patient.getFirstName() + " " + patient.getLastName()).trim();
        QueueEntry entry = new QueueEntry(ticket, name, departmentId, priority,
                PatientSource.WALK_IN, LocalDateTime.now());
        entry.setPatientId(patient.getId());
        queueEntryRepository.save(entry);
        log.info("Walk-in registered: {} -> {} ({})", name, ticket, department.getName());
        return QueueEntryResponse.from(entry);
    }

    /** Simple status transition for queue entries (waiting → active/completed). */
    @Transactional
    public QueueEntryResponse updateStatus(Long entryId, String status) {
        QueueEntry entry = queueEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Queue entry not found"));
        QueueStatus target = QueueStatus.valueOf(status.toUpperCase());

        boolean legal = switch (entry.getStatus()) {
            case WAITING -> target == QueueStatus.IN_CONSULTATION
                    || target == QueueStatus.NO_SHOW || target == QueueStatus.SKIPPED
                    || target == QueueStatus.CANCELLED;
            case IN_CONSULTATION -> target == QueueStatus.COMPLETED
                    || target == QueueStatus.NO_SHOW;
            case CANCELLED -> false; // terminal
            default -> false; // completed / no_show / skipped are terminal
        };
        if (!legal) {
            throw new ConflictException("Cannot move ticket " + entry.getTicketNumber()
                    + " from '" + entry.getStatus().name().toLowerCase()
                    + "' to '" + status + "'");
        }
        entry.setStatus(target);
        if (target == QueueStatus.IN_CONSULTATION && entry.getCalledAt() == null) {
            entry.setCalledAt(LocalDateTime.now());
        }
        queueEntryRepository.save(entry);
        return QueueEntryResponse.from(entry);
    }

    // ===== Helpers =====

    private QueueDepartmentResponse summarize(Department d, List<QueueEntry> all) {
        String deptId = String.valueOf(d.getId());
        List<QueueEntry> deptEntries = all.stream()
                .filter(e -> deptId.equals(e.getDepartmentId()))
                .toList();
        List<QueueEntry> waiting = deptEntries.stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING)
                .toList();
        QueueEntry serving = deptEntries.stream()
                .filter(e -> e.getStatus() == QueueStatus.IN_CONSULTATION)
                .findFirst().orElse(null);
        int longest = waiting.stream()
                .mapToInt(e -> (int) Math.max(0,
                        Duration.between(e.getCheckInAt(), LocalDateTime.now()).toMinutes()))
                .max().orElse(0);

        String severity = longest > 40 ? "critical" : longest > 25 ? "warning" : "ok";
        return new QueueDepartmentResponse(
                deptId, d.getName(), waiting.size(),
                serving != null ? serving.getTicketNumber() : null,
                longest, severity);
    }
}
