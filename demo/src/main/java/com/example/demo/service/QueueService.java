package com.example.demo.service;

import com.example.demo.dto.QueueDepartmentResponse;
import com.example.demo.dto.QueueEntryResponse;
import com.example.demo.exception.ConflictException;
import com.example.demo.model.Department;
import com.example.demo.model.QueueEntry;
import com.example.demo.model.QueuePriority;
import com.example.demo.model.QueueStatus;
import com.example.demo.model.StaffMember;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.QueueEntryRepository;
import com.example.demo.repository.StaffMemberRepository;
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

    private final QueueEntryRepository queueEntryRepository;
    private final DepartmentRepository departmentRepository;
    private final StaffMemberRepository staffMemberRepository;

    public QueueService(QueueEntryRepository queueEntryRepository,
                        DepartmentRepository departmentRepository,
                        StaffMemberRepository staffMemberRepository) {
        this.queueEntryRepository = queueEntryRepository;
        this.departmentRepository = departmentRepository;
        this.staffMemberRepository = staffMemberRepository;
    }

    /** Per-department summaries for the sidebar / dept tabs. */
    public List<QueueDepartmentResponse> departments(Long facilityId) {
        List<QueueEntry> all = queueEntryRepository.findAll();
        return departmentRepository.findByFacilityId(facilityId).stream()
                .map(d -> summarize(d, all))
                .toList();
    }

    /** Entries for one department, waiting first then by check-in time. */
    public List<QueueEntryResponse> entries(String departmentId) {
        return queueEntryRepository.findByDepartmentId(departmentId).stream()
                .sorted(Comparator
                        .comparing((QueueEntry e) -> e.getStatus() == QueueStatus.WAITING ? 0 : 1)
                        .thenComparing(QueueEntry::getCheckInAt))
                .map(QueueEntryResponse::from)
                .toList();
    }

    /** Move the next (or a specific) waiting entry into consultation. */
    @Transactional
    public QueueEntryResponse callNext(String departmentId, String entryId, Long staffId) {
        List<QueueEntry> waiting = queueEntryRepository
                .findByDepartmentIdAndStatus(departmentId, QueueStatus.WAITING).stream()
                .sorted(Comparator.comparing(QueueEntry::getCheckInAt))
                .toList();
        if (waiting.isEmpty()) {
            throw new ConflictException("No patients waiting in this queue.");
        }
        QueueEntry target = entryId != null
                ? waiting.stream().filter(e -> e.getId().toString().equals(entryId))
                        .findFirst()
                        .orElseThrow(() -> new ConflictException(
                                "That ticket is no longer waiting in this queue."))
                : waiting.get(0);

        target.setStatus(QueueStatus.IN_CONSULTATION);
        target.setCalledAt(LocalDateTime.now());
        if (target.getClinician() == null) {
            staffMemberRepository.findById(staffId)
                    .map(StaffMember::getName)
                    .ifPresent(target::setClinician);
        }
        queueEntryRepository.save(target);
        return QueueEntryResponse.from(target);
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
