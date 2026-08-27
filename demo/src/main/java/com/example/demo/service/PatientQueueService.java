package com.example.demo.service;

import com.example.demo.dto.QueueTicketResponse;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Booking;
import com.example.demo.model.BookingStatus;
import com.example.demo.model.Department;
import com.example.demo.model.Doctor;
import com.example.demo.model.Hospital;
import com.example.demo.model.OperationalSettings;
import com.example.demo.model.Patient;
import com.example.demo.model.PatientSource;
import com.example.demo.model.QueueEntry;
import com.example.demo.model.QueuePriority;
import com.example.demo.model.QueueStatus;
import com.example.demo.repository.BookingRepository;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.OperationalSettingsRepository;
import com.example.demo.repository.PatientRepository;
import com.example.demo.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Patient-facing queue ticket + check-in (ARCHITECTURE.md G9/G10 / P5).
 */
@Service
public class PatientQueueService {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private static final List<QueueStatus> ACTIVE =
            List.of(QueueStatus.WAITING, QueueStatus.IN_CONSULTATION);

    private final QueueEntryRepository queueEntryRepository;
    private final BookingRepository bookingRepository;
    private final DepartmentRepository departmentRepository;
    private final PatientRepository patientRepository;
    private final OperationalSettingsRepository operationalSettingsRepository;

    public PatientQueueService(QueueEntryRepository queueEntryRepository,
                               BookingRepository bookingRepository,
                               DepartmentRepository departmentRepository,
                               PatientRepository patientRepository,
                               OperationalSettingsRepository operationalSettingsRepository) {
        this.queueEntryRepository = queueEntryRepository;
        this.bookingRepository = bookingRepository;
        this.departmentRepository = departmentRepository;
        this.patientRepository = patientRepository;
        this.operationalSettingsRepository = operationalSettingsRepository;
    }

    @Transactional(readOnly = true)
    public QueueTicketResponse myTicket(Long patientId) {
        QueueEntry mine = findActiveEntry(patientId);
        if (mine == null) {
            throw new ResourceNotFoundException(
                    "No active queue ticket. Check in at the hospital for today's booking first.");
        }
        return toTicket(mine, patientId);
    }

    @Transactional
    public QueueTicketResponse checkIn(Long patientId) {
        QueueEntry existing = findActiveEntry(patientId);
        if (existing != null) {
            return toTicket(existing, patientId);
        }

        Booking booking = findCheckInTarget(patientId);
        if (booking == null) {
            throw new ResourceNotFoundException(
                    "No upcoming booking to check in. Book an appointment first, then check in on the day of your visit.");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("That booking was cancelled and cannot be checked in.");
        }

        booking.setCheckedIn(true);
        booking.setCheckInTime(LocalDateTime.now());
        booking.setAppointmentStatus("checked_in");
        bookingRepository.save(booking);

        QueueEntry entry = queueEntryRepository.findByBookingId(booking.getId()).orElse(null);
        if (entry == null) {
            entry = buildQueueEntry(booking);
            queueEntryRepository.save(entry);
        } else {
            entry.setPatientId(patientId);
            entry.setBookingId(booking.getId());
            queueEntryRepository.save(entry);
        }
        return toTicket(entry, patientId);
    }

    private QueueEntry findActiveEntry(Long patientId) {
        List<QueueEntry> byPatient = queueEntryRepository.findByPatientIdAndStatusIn(patientId, ACTIVE);
        if (!byPatient.isEmpty()) {
            return byPatient.stream()
                    .max(Comparator.comparing(QueueEntry::getCheckInAt))
                    .orElse(null);
        }
        Patient p = patientRepository.findById(patientId).orElse(null);
        if (p == null) return null;
        String name = (p.getFirstName() + " " + p.getLastName()).trim();
        return queueEntryRepository.findByPatientNameIgnoreCase(name).stream()
                .filter(e -> ACTIVE.contains(e.getStatus()))
                .max(Comparator.comparing(QueueEntry::getCheckInAt))
                .orElse(null);
    }

    private Booking findCheckInTarget(Long patientId) {
        List<Booking> bookings = bookingRepository.findByPatientId(patientId);
        LocalDate today = LocalDate.now();
        return bookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .filter(b -> !Boolean.TRUE.equals(b.getCheckedIn())
                        || queueEntryRepository.findByBookingId(b.getId())
                                .filter(e -> ACTIVE.contains(e.getStatus()))
                                .isPresent())
                .filter(b -> appointmentDate(b) == null || !appointmentDate(b).isBefore(today))
                .min(Comparator.comparing(b -> appointmentDate(b) != null
                        ? appointmentDate(b) : LocalDate.MAX))
                .orElse(null);
    }

    private QueueEntry buildQueueEntry(Booking booking) {
        String deptId = String.valueOf(booking.getDepartment().getId());
        LocalDateTime now = LocalDateTime.now();
        String prefix = departmentPrefix(booking.getDepartment());
        // Collision-proof: next ticket = highest sequence ever issued for this prefix + 1.
        // The old "count of today's entries + 1" scheme collided with historical/seed
        // tickets (e.g. C-001 from a previous day) → duplicate ticket numbers.
        long nextSeq = queueEntryRepository.maxTicketSequenceForPrefix(prefix) + 1;
        String ticket = prefix + "-" + String.format("%03d", nextSeq);

        QueuePriority priority;
        try {
            priority = QueuePriority.valueOf(
                    (booking.getPriority() == null ? "routine" : booking.getPriority())
                            .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            priority = QueuePriority.ROUTINE;
        }

        Patient p = booking.getPatient();
        String name = p.getFirstName() + " " + p.getLastName();
        QueueEntry entry = new QueueEntry(ticket, name.trim(), deptId,
                priority, PatientSource.APPOINTMENT, now);
        entry.setPatientId(p.getId());
        entry.setBookingId(booking.getId());
        if (booking.getDoctor() != null) {
            entry.setClinician("Dr. " + booking.getDoctor().getLastName());
        }
        Department dept = booking.getDepartment();
        if (dept.getRooms() != null && dept.getRooms() > 0) {
            entry.setRoom(String.valueOf(dept.getRooms()));
        }
        return entry;
    }

    private QueueTicketResponse toTicket(QueueEntry mine, Long patientId) {
        String deptId = mine.getDepartmentId();
        List<QueueEntry> dept = queueEntryRepository.findByDepartmentId(deptId);

        QueueEntry serving = dept.stream()
                .filter(e -> e.getStatus() == QueueStatus.IN_CONSULTATION)
                .max(Comparator.comparing(QueueEntry::getCalledAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        int currentNumber = serving != null
                ? ticketNumeric(serving.getTicketNumber())
                : nextCalledNumber(dept);
        int userNumber = ticketNumeric(mine.getTicketNumber());

        int positionsAhead = 0;
        if (mine.getStatus() != QueueStatus.IN_CONSULTATION) {
            positionsAhead = (int) dept.stream()
                    .filter(e -> e.getStatus() == QueueStatus.WAITING
                            || e.getStatus() == QueueStatus.IN_CONSULTATION)
                    .filter(e -> !e.getId().equals(mine.getId()))
                    .filter(e -> !e.getCheckInAt().isAfter(mine.getCheckInAt()))
                    .count();
        }

        int slotMins = slotMinutes(mine, patientId);
        int waitMins = positionsAhead * slotMins;
        String estimated = LocalTime.now().plusMinutes(waitMins).format(DISPLAY_TIME)
                .replace("AM", "AM").replace("PM", "PM");

        Booking booking = mine.getBookingId() != null
                ? bookingRepository.findById(mine.getBookingId()).orElse(null)
                : null;
        // Prefer the booking's hospital/department; fall back to the queue
        // entry's own department (seeded entries and walk-ins have no booking,
        // so hardcoded "Hospital"/"Department" placeholders leaked into the
        // ticket — ARCHITECTURE.md P5 queue shape).
        Department department = booking != null ? booking.getDepartment()
                : resolveDepartment(mine.getDepartmentId());
        Hospital hospital = department != null ? department.getHospital() : null;
        if (hospital == null && booking != null) {
            hospital = booking.getHospital();
        }
        String hospitalName = hospital != null ? hospital.getName() : "—";
        String departmentName = department != null ? department.getName() : "—";
        String doctorName = doctorNameOf(mine, booking);
        String room = mine.getRoom() != null ? mine.getRoom() : "—";

        return new QueueTicketResponse(
                hospitalName, departmentName, doctorName,
                currentNumber, userNumber, waitMins, room, estimated);
    }

    private Department resolveDepartment(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) return null;
        try {
            return departmentRepository.findById(Long.parseLong(departmentId)).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int nextCalledNumber(List<QueueEntry> dept) {
        return dept.stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING)
                .min(Comparator.comparing(QueueEntry::getCheckInAt))
                .map(e -> ticketNumeric(e.getTicketNumber()))
                .orElse(0);
    }

    static int ticketNumeric(String ticketNumber) {
        if (ticketNumber == null) return 0;
        String digits = ticketNumber.replaceAll("\\D+", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int slotMinutes(QueueEntry mine, Long patientId) {
        Booking booking = mine.getBookingId() != null
                ? bookingRepository.findById(mine.getBookingId()).orElse(null)
                : null;
        Long facilityId = booking != null && booking.getHospital() != null
                ? booking.getHospital().getId()
                : null;
        if (facilityId == null) {
            return 20;
        }
        return operationalSettingsRepository.findByFacilityId(facilityId)
                .map(OperationalSettings::getAppointmentSlotMinutes)
                .filter(m -> m > 0)
                .orElse(20);
    }

    private static String doctorNameOf(QueueEntry mine, Booking booking) {
        if (mine.getClinician() != null && !mine.getClinician().isBlank()) {
            return mine.getClinician();
        }
        if (booking != null && booking.getDoctor() != null) {
            Doctor d = booking.getDoctor();
            return "Dr. " + d.getLastName();
        }
        return "Unassigned";
    }

    private static LocalDate appointmentDate(Booking b) {
        if (b.getTimeSlot() != null && b.getTimeSlot().getDate() != null) {
            return b.getTimeSlot().getDate();
        }
        return b.getBookingDate() != null ? b.getBookingDate().toLocalDate() : null;
    }

    private static String departmentPrefix(Department d) {
        String code = d.getAbbreviation();
        if (code != null && !code.isBlank()) {
            return code.substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return "D";
    }
}
