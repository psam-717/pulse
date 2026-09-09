package com.example.demo.service;

import com.example.demo.dto.AppointmentDepartmentResponse;
import com.example.demo.dto.AppointmentResponse;
import com.example.demo.dto.AppointmentStatsResponse;
import com.example.demo.exception.ConflictException;
import com.example.demo.model.*;
import com.example.demo.repository.BookingRepository;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.QueueEntryRepository;
import com.example.demo.repository.TimeSlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Facility-plane appointments (BACKEND_SPEC.md §6.1) — a projection over the
 * Booking entity (D4: mobile booking feeds the web appointment). The status
 * state machine (§7.1) is enforced server-side:
 *
 *   scheduled → confirmed | cancelled
 *   confirmed → checked_in | no_show
 *   checked_in → confirmed (undo)
 *   completed / cancelled / no_show → terminal
 *
 * checked_in is the hand-off point to the Live Queue (D6): the transition
 * synchronously creates a QueueEntry (source=appointment) in the same
 * transaction.
 */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private static final Set<String> VALID_STATUSES =
            Set.of("scheduled", "confirmed", "checked_in", "completed", "cancelled", "no_show");

    private final BookingRepository bookingRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final DepartmentRepository departmentRepository;
    private final com.example.demo.repository.StaffMemberRepository staffMemberRepository;
    private final PatientNotificationService patientNotificationService;
    private final TimeSlotRepository timeSlotRepository;

    public AppointmentService(BookingRepository bookingRepository,
                              QueueEntryRepository queueEntryRepository,
                              DepartmentRepository departmentRepository,
                              com.example.demo.repository.StaffMemberRepository staffMemberRepository,
                              PatientNotificationService patientNotificationService,
                              TimeSlotRepository timeSlotRepository) {
        this.bookingRepository = bookingRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.departmentRepository = departmentRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.patientNotificationService = patientNotificationService;
        this.timeSlotRepository = timeSlotRepository;
    }

    // ===== Read =====

    /** Day view: GET /appointments?date&departmentId?&status? (defaults to today).
     *  staffId scopes to one clinician (workspace "My Appointments") — matched
     *  by the stable staff↔legacy-doctor email link, NOT the display name, so
     *  profile renames never orphan a doctor's appointments. */
    public List<AppointmentResponse> listForDay(Long facilityId, LocalDate date,
                                                String departmentId, String status,
                                                Long staffId) {
        List<Booking> bookings = (departmentId == null || departmentId.isBlank() || "all".equals(departmentId))
                ? bookingRepository.findByTimeSlot_Date(date)
                : bookingRepository.findByTimeSlot_DateAndDepartmentId(date, parseDeptId(departmentId));
        return bookings.stream()
                .filter(b -> belongsToFacility(b, facilityId))
                .filter(b -> staffId == null || matchesStaff(b, staffId))
                .filter(b -> status == null || status.isBlank() || "all".equals(status)
                        || status.equals(deriveStatus(b)))
                .sorted(Comparator.comparing(AppointmentService::scheduledAt))
                .map(this::toResponse)
                .toList();
    }

    /** Range view: GET /appointments?from&to (week/month calendar). */
    public List<AppointmentResponse> listForRange(Long facilityId, LocalDate from, LocalDate to,
                                                  Long staffId) {
        return bookingRepository.findByTimeSlot_DateBetween(from, to).stream()
                .filter(b -> belongsToFacility(b, facilityId))
                .filter(b -> staffId == null || matchesStaff(b, staffId))
                .sorted(Comparator.comparing(AppointmentService::scheduledAt))
                .map(this::toResponse)
                .toList();
    }

    public AppointmentStatsResponse statsForDay(Long facilityId, LocalDate date) {
        List<AppointmentResponse> day = listForDay(facilityId, date, null, null, null);
        return new AppointmentStatsResponse(
                day.size(),
                countStatus(day, "scheduled"),
                countStatus(day, "confirmed"),
                countStatus(day, "checked_in"),
                countStatus(day, "completed"),
                countStatus(day, "cancelled"),
                countStatus(day, "no_show"));
    }

    private static int countStatus(List<AppointmentResponse> appointments, String status) {
        return (int) appointments.stream().filter(a -> a.status().equals(status)).count();
    }

    public List<AppointmentDepartmentResponse> departments(Long facilityId) {
        return departmentRepository.findByFacilityId(facilityId).stream()
                .map(d -> new AppointmentDepartmentResponse(String.valueOf(d.getId()), d.getName()))
                .toList();
    }

    // ===== Write =====

    /** PATCH /appointments/{id} — state machine + checked_in queue hand-off. */
    @Transactional
    public AppointmentResponse updateStatus(Long facilityId, Long id, String targetStatus) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        if (!belongsToFacility(booking, facilityId)) {
            throw new IllegalArgumentException("Appointment not found in this facility");
        }
        if (targetStatus == null || !VALID_STATUSES.contains(targetStatus)) {
            throw new IllegalArgumentException(
                    "Invalid status '" + targetStatus
                            + "'. Must be one of: scheduled, confirmed, checked_in, completed, cancelled, no_show");
        }

        String current = deriveStatus(booking);
        if (!isLegalTransition(current, targetStatus)) {
            throw new ConflictException(
                    "Cannot move appointment " + referenceOf(booking) + " from '" + current
                            + "' to '" + targetStatus + "'");
        }

        booking.setAppointmentStatus(targetStatus);

        if ("checked_in".equals(targetStatus)) {
            // Hand-off to Live Queue (D6): mark arrival + create the queue entry
            booking.setCheckedIn(true);
            booking.setCheckInTime(LocalDateTime.now());
            queueEntryRepository.save(buildQueueEntry(booking));
            log.info("Appointment {} checked in -> queue entry created", referenceOf(booking));
        } else if ("confirmed".equals(targetStatus) && "checked_in".equals(current)) {
            // Undo: clear arrival + remove the hand-off queue entries
            booking.setCheckedIn(false);
            booking.setCheckInTime(null);
            removeQueueEntriesFor(booking);
            log.info("Appointment {} check-in undone", referenceOf(booking));
        }
        if ("cancelled".equals(targetStatus)) {
            // Staff cancel is a real cancel (mirrors BookingService.cancelBooking):
            // flip the legacy patient-facing status so cancelled appointments stop
            // leaking into the patient's outstanding-payments list, and free the
            // slot so the day can be re-booked.
            booking.setStatus(BookingStatus.CANCELLED);
            TimeSlot slot = booking.getTimeSlot();
            if (slot != null && slot.isBooked()) {
                slot.setBooked(false);
                timeSlotRepository.save(slot);
            }
            log.info("Appointment {} cancelled by staff — booking cancelled, slot freed",
                    referenceOf(booking));
        }
        if ("confirmed".equals(targetStatus) || "cancelled".equals(targetStatus)) {
            notifyPatientOfBookingChange(booking, targetStatus);
        }

        return toResponse(bookingRepository.save(booking));
    }

    /** Patient in-app notification when a booking is approved or cancelled. */
    private void notifyPatientOfBookingChange(Booking booking, String targetStatus) {
        if (booking.getPatient() == null) return;
        boolean approved = "confirmed".equals(targetStatus);
        String date = scheduledAt(booking) != null ? scheduledAt(booking).toLocalDate().toString() : "—";
        String dept = booking.getDepartment() != null ? booking.getDepartment().getName() : "—";
        String title = approved ? "Booking approved" : "Booking cancelled";
        String body = referenceOf(booking) + " · " + dept + " · " + date
                + (approved ? " has been approved." : " was cancelled.");
        patientNotificationService.create(booking.getPatient().getId(),
                "appointment", title, body, null);
    }

    /** Mark payment state for an appointment (admin/doctor, facility-scoped). */
    @Transactional
    public AppointmentResponse updatePayment(Long facilityId, Long id, String rawStatus) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        if (!belongsToFacility(booking, facilityId)) {
            throw new IllegalArgumentException("Appointment not found in this facility");
        }
        com.example.demo.model.PaymentStatus payment;
        try {
            payment = com.example.demo.model.PaymentStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("paymentStatus must be one of: pending, paid, failed, refunded");
        }
        booking.setPaymentStatus(payment);
        return toResponse(bookingRepository.save(booking));
    }

    /** Patient's own bookings (mobile "Bookings" section), newest scheduled first. */
    @Transactional(readOnly = true)
    public List<com.example.demo.dto.PatientBookingResponse> patientBookings(Long patientId) {
        return bookingRepository.findByPatientId(patientId).stream()
                .sorted(Comparator.comparing((Booking b) -> {
                    LocalDateTime at = scheduledAt(b);
                    return at != null ? at : LocalDateTime.MIN;
                }).reversed())
                .map(this::toPatientBooking)
                .toList();
    }

    private com.example.demo.dto.PatientBookingResponse toPatientBooking(Booking b) {
        String doctorName = b.getDoctor() != null
                ? b.getDoctor().getFirstName() + " " + b.getDoctor().getLastName()
                : "Unassigned";
        LocalDateTime at = scheduledAt(b);
        return new com.example.demo.dto.PatientBookingResponse(
                String.valueOf(b.getId()),
                referenceOf(b),
                b.getHospital() != null ? b.getHospital().getName() : null,
                b.getDepartment() != null ? b.getDepartment().getName() : null,
                doctorName,
                at != null ? at.toString() : null,
                deriveStatus(b),
                paymentStatusOf(b));
    }

    // ===== Status derivation & transitions =====

    /**
     * Facility-plane status: the appointmentStatus column wins; otherwise
     * derived from the legacy patient-facing booking state.
     */
    static String deriveStatus(Booking b) {
        if (b.getAppointmentStatus() != null) {
            return b.getAppointmentStatus();
        }
        if (Boolean.TRUE.equals(b.getCheckedIn())) {
            return "checked_in";
        }
        return switch (b.getStatus()) {
            case PENDING_PAYMENT -> "scheduled";
            case CONFIRMED -> "confirmed";
            case CANCELLED -> "cancelled";
        };
    }

    /** §7.1 transition table — anything not listed is rejected with 409. */
    private static boolean isLegalTransition(String current, String target) {
        return switch (current) {
            case "scheduled" -> "confirmed".equals(target) || "cancelled".equals(target);
            case "confirmed" -> "checked_in".equals(target) || "no_show".equals(target);
            case "checked_in" -> "confirmed".equals(target); // undo
            default -> false; // completed / cancelled / no_show are terminal
        };
    }

    // ===== Queue hand-off (D6) =====

    private QueueEntry buildQueueEntry(Booking booking) {
        String deptId = String.valueOf(booking.getDepartment().getId());
        LocalDateTime now = LocalDateTime.now();
        String prefix = departmentPrefix(booking.getDepartment());
        // Collision-proof: next ticket = highest sequence ever issued for this
        // prefix + 1 (same scheme as QueueService walk-ins / PatientQueueService).
        // The old "count of today's entries + 1" scheme collided with historical
        // tickets — e.g. K-001 issued yesterday left the count at 0 today, so the
        // generator re-issued K-001 and the check-in 409'd on the unique
        // uk_queue_entries_ticket_number index ("nothing happens" on the button).
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

        QueueEntry entry = new QueueEntry(ticket, patientNameOf(booking), deptId,
                priority, PatientSource.APPOINTMENT, now);
        if (booking.getPatient() != null) {
            entry.setPatientId(booking.getPatient().getId());
        }
        entry.setBookingId(booking.getId());
        return entry;
    }

    private void removeQueueEntriesFor(Booking booking) {
        String deptId = String.valueOf(booking.getDepartment().getId());
        List<QueueEntry> entries = queueEntryRepository
                .findByDepartmentIdAndPatientNameAndSourceAndStatus(
                        deptId, patientNameOf(booking), PatientSource.APPOINTMENT, QueueStatus.WAITING);
        queueEntryRepository.deleteAll(entries);
    }

    private static String departmentPrefix(Department d) {
        String code = d.getAbbreviation();
        if (code != null && !code.isBlank()) {
            return code.substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return "D";
    }

    // ===== Mapping =====

    private AppointmentResponse toResponse(Booking b) {
        String reference = referenceOf(b);
        String doctorName = b.getDoctor() != null
                ? b.getDoctor().getFirstName() + " " + b.getDoctor().getLastName()
                : "Unassigned";
        LocalDateTime at = scheduledAt(b);
        return new AppointmentResponse(
                String.valueOf(b.getId()),
                reference,
                String.valueOf(b.getPatient().getId()),
                patientNameOf(b),
                String.valueOf(b.getDepartment().getId()),
                b.getDepartment().getName(),
                doctorName,
                at != null ? at.toString() : null,
                durationMinutes(b),
                deriveStatus(b),
                "in_person",
                b.getPriority() != null ? b.getPriority() : "routine",
                null,
                paymentStatusOf(b));
    }

    private static String paymentStatusOf(Booking b) {
        if (b.getPaymentStatus() == null) return "pending";
        return b.getPaymentStatus().name().toLowerCase();
    }

    private static LocalDateTime scheduledAt(Booking b) {
        TimeSlot slot = b.getTimeSlot();
        if (slot == null || slot.getDate() == null) return null;
        LocalTime start = slot.getStartTime();
        return slot.getDate().atTime(start != null ? start : LocalTime.MIDNIGHT);
    }

    private static int durationMinutes(Booking b) {
        TimeSlot slot = b.getTimeSlot();
        if (slot != null && slot.getStartTime() != null && slot.getEndTime() != null) {
            long minutes = ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime());
            if (minutes > 0) return (int) minutes;
        }
        Doctor doctor = b.getDoctor();
        return doctor != null && doctor.getConsultationDuration() != null
                ? doctor.getConsultationDuration() : 20;
    }

    private static String patientNameOf(Booking b) {
        Patient p = b.getPatient();
        return (p.getFirstName() + " " + p.getLastName()).trim();
    }

    private static String referenceOf(Booking b) {
        return String.format("APT-%04d", b.getId());
    }

    /** Tenant boundary: booking belongs to the facility via its department (or hospital). */
    private boolean belongsToFacility(Booking b, Long facilityId) {
        Department d = b.getDepartment();
        Long deptFacility = d != null && d.getFacilityId() != null
                ? d.getFacilityId()
                : (b.getHospital() != null ? b.getHospital().getId() : null);
        return facilityId.equals(deptFacility);
    }

    /** Staff↔legacy-doctor match by email — stable across display-name edits. */
    private boolean matchesStaff(Booking b, Long staffId) {
        if (b.getDoctor() == null || b.getDoctor().getEmail() == null) return false;
        return staffMemberRepository.findById(staffId)
                .map(s -> s.getEmail() != null
                        && s.getEmail().equalsIgnoreCase(b.getDoctor().getEmail()))
                .orElse(false);
    }

    private static Long parseDeptId(String departmentId) {
        try {
            return Long.parseLong(departmentId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid departmentId '" + departmentId + "'");
        }
    }
}
