package com.example.demo.service;

import com.example.demo.dto.AvailabilityResponse;
import com.example.demo.dto.AvailabilityResponse.DaySlots;
import com.example.demo.dto.AvailabilityResponse.SlotItem;
import com.example.demo.dto.BookingRequest;
import com.example.demo.dto.BookingResponse;
import com.example.demo.dto.BookingSummaryResponse;
import com.example.demo.dto.DoctorAvailabilityResponse;
import com.example.demo.dto.MobileBookingRequest;
import com.example.demo.dto.RescheduleRequest;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.SurchargeRequiredException;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final HospitalRepository hospitalRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final BookingRepository bookingRepository;
    private final PatientRepository patientRepository;
    private final MobileDiscoveryService mobileDiscoveryService;
    private final OperationalSettingsRepository operationalSettingsRepository;
    private final com.example.demo.repository.StaffMemberRepository staffMemberRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final PatientNotificationService patientNotificationService;
    private final NotificationService notificationService;

    private static final int DEFAULT_DEADLINE_HOURS = 48;

    public BookingService(HospitalRepository hospitalRepository,
                          DepartmentRepository departmentRepository,
                          DoctorRepository doctorRepository,
                          TimeSlotRepository timeSlotRepository,
                          BookingRepository bookingRepository,
                          PatientRepository patientRepository,
                          MobileDiscoveryService mobileDiscoveryService,
                          OperationalSettingsRepository operationalSettingsRepository,
                          com.example.demo.repository.StaffMemberRepository staffMemberRepository,
                          QueueEntryRepository queueEntryRepository,
                          PatientNotificationService patientNotificationService,
                          NotificationService notificationService) {
        this.hospitalRepository = hospitalRepository;
        this.departmentRepository = departmentRepository;
        this.doctorRepository = doctorRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.bookingRepository = bookingRepository;
        this.patientRepository = patientRepository;
        this.mobileDiscoveryService = mobileDiscoveryService;
        this.operationalSettingsRepository = operationalSettingsRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.patientNotificationService = patientNotificationService;
        this.notificationService = notificationService;
    }

    public Page<Hospital> listHospitals(Pageable pageable) {
        return hospitalRepository.findAll(pageable);
    }

    public List<Hospital> listHospitals() {
        return hospitalRepository.findAll();
    }

    public Page<Department> listDepartments(Long hospitalId, Pageable pageable) {
        if (!hospitalRepository.existsById(hospitalId)) {
            throw new IllegalArgumentException("Hospital not found");
        }
        return departmentRepository.findByHospitalId(hospitalId, pageable);
    }

    public List<Department> listDepartments(Long hospitalId) {
        if (!hospitalRepository.existsById(hospitalId)) {
            throw new IllegalArgumentException("Hospital not found");
        }
        return departmentRepository.findByHospitalId(hospitalId);
    }

    public Page<Doctor> listDoctors(Long departmentId, Pageable pageable) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new IllegalArgumentException("Department not found");
        }
        return doctorRepository.findByDepartmentId(departmentId, pageable);
    }

    /**
     * Public doctor listing with the online-booking eligibility flag.
     * {@code bookableOnline} is true only for doctors the mobile booking flow
     * can actually resolve to (OnlineBookingSupport staff-link rule), so
     * clients can hide departments whose doctors would 409 on booking.
     */
    public Page<DoctorAvailabilityResponse> listDoctorsWithAvailability(Long departmentId, Pageable pageable) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        Page<Doctor> doctors = doctorRepository.findByDepartmentId(departmentId, pageable);
        Set<Long> linkedIds = OnlineBookingSupport.staffLinkedDoctors(
                        doctors.getContent(), staffMemberRepository, department.getFacilityId())
                .stream()
                .map(Doctor::getId)
                .collect(Collectors.toSet());
        return doctors.map(d -> new DoctorAvailabilityResponse(
                d.getId(),
                d.getFirstName(),
                d.getLastName(),
                d.getSpecialization(),
                d.getEmail(),
                d.getPhone(),
                departmentId,
                d.getHospital() == null ? null : d.getHospital().getId(),
                linkedIds.contains(d.getId())));
    }

    public List<Doctor> listDoctors(Long departmentId) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new IllegalArgumentException("Department not found");
        }
        return doctorRepository.findByDepartmentId(departmentId);
    }

    public List<TimeSlot> listAvailableSlots(Long doctorId, LocalDate date) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new IllegalArgumentException("Doctor not found");
        }
        return timeSlotRepository.findByDoctorIdAndDateAndIsBooked(doctorId, date, false);
    }

    public Page<BookingResponse> listPatientBookings(Long patientId, Pageable pageable) {
        return bookingRepository.findByPatientId(patientId, pageable)
                .map(this::toResponse);
    }

    public Page<BookingResponse> listDoctorAppointments(Long doctorId, Pageable pageable) {
        return bookingRepository.findByDoctorId(doctorId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long authenticatedUserId, String role, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        // Ownership check (same pattern as getBookingSummary)
        if (!"ROLE_SUPER_ADMIN".equals(role)) {
            if (!booking.getPatient().getId().equals(authenticatedUserId)) {
                throw new AccessDeniedException("You can only cancel your own bookings");
            }
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking is already cancelled");
        }

        // Free the time slot
        booking.getTimeSlot().setBooked(false);
        timeSlotRepository.save(booking.getTimeSlot());

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setPaymentStatus(PaymentStatus.REFUNDED);
        bookingRepository.save(booking);

        // Patient in-app notification (same shape as the staff-cancel notice).
        notifyCancelled(booking);
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest request, Long authenticatedUserId, String role) {
        // Fix #1: Resolve patient from JWT — PATIENT role uses their own ID,
        // DOCTOR and SUPER_ADMIN can book on behalf of patients via the request
        Patient patient;
        if ("ROLE_PATIENT".equals(role)) {
            patient = patientRepository.findById(authenticatedUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        } else {
            // DOCTOR or SUPER_ADMIN must provide a patientId in the request
            if (request.patientId() == null) {
                throw new IllegalArgumentException("patientId is required when booking on behalf of a patient");
            }
            patient = patientRepository.findById(request.patientId())
                    .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        }

        TimeSlot slot = timeSlotRepository.findById(request.timeSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        if (slot.isBooked()) {
            throw new IllegalStateException("This time slot is already booked");
        }

        // Fix #2: Reject past-date slots
        if (slot.getDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot book a time slot in the past");
        }

        // Fix #3: Prevent duplicate booking by same patient for same slot
        if (bookingRepository.existsByPatientIdAndTimeSlotId(patient.getId(), slot.getId())) {
            throw new IllegalStateException("You already have a booking for this time slot");
        }

        Doctor doctor = slot.getDoctor();
        Department department = doctor.getDepartment();
        Hospital hospital = doctor.getHospital();

        slot.setBooked(true);
        timeSlotRepository.save(slot);

        Booking booking = new Booking(patient, doctor, department, hospital, slot, department.getConsultationFee());
        booking.setPayByDeadline(LocalDateTime.now().plusHours(deadlineHoursFor(hospital)));
        bookingRepository.save(booking);

        return toResponse(booking);
    }

    @Transactional
    public BookingResponse updatePaymentStatus(Long bookingId, String paymentStatusStr) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        PaymentStatus paymentStatus;
        try {
            paymentStatus = PaymentStatus.valueOf(paymentStatusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid payment status: " + paymentStatusStr);
        }

        booking.setPaymentStatus(paymentStatus);
        if (paymentStatus == PaymentStatus.PAID) {
            booking.setStatus(BookingStatus.CONFIRMED);
        } else if (paymentStatus == PaymentStatus.FAILED) {
            booking.setStatus(BookingStatus.CANCELLED);
            // Free the slot back up
            booking.getTimeSlot().setBooked(false);
            timeSlotRepository.save(booking.getTimeSlot());
        }

        bookingRepository.save(booking);
        return toResponse(booking);
    }

    public BookingResponse getBookingSummary(Long bookingId, Long authenticatedUserId, String role) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        // Role-based access control
        if (!"ROLE_SUPER_ADMIN".equals(role)) {
            if ("ROLE_PATIENT".equals(role)) {
                if (!booking.getPatient().getId().equals(authenticatedUserId)) {
                    throw new AccessDeniedException("You can only view your own bookings");
                }
            } else if ("ROLE_DOCTOR".equals(role)) {
                if (booking.getDoctor() == null || !booking.getDoctor().getId().equals(authenticatedUserId)) {
                    throw new AccessDeniedException("You can only view your own appointments");
                }
            }
            // HOSPITAL_ADMIN is not scoped to individual bookings
        }

        return toResponse(booking);
    }

    // ===== P3 — mobile book / reschedule / outstanding / expiry =====

    @Transactional
    public BookingSummaryResponse createMobileBooking(Long patientId, MobileBookingRequest req) {
        if (req == null || req.departmentId() == null) {
            throw new IllegalArgumentException(
                    "departmentId is required. Use GET /api/mobile/hospitals/{id}/departments.");
        }
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        Department dept = departmentRepository.findById(req.departmentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Department not found. Use GET /api/mobile/hospitals/{id}/departments."));
        LocalDate date = parseDate(req.date(), "date");
        LocalTime time = parseTime(req.time(), "time");
        if (date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot book a date in the past.");
        }

        assertSlotBookable(dept.getId(), date, time, null);
        Doctor doctor = pickDoctor(dept.getId(), date, time);
        TimeSlot slot = claimSlot(doctor, date, time);

        Hospital hospital = resolveHospital(dept);
        Booking booking = new Booking(patient, doctor, dept, hospital, slot, dept.getConsultationFee());
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setPaymentStatus(PaymentStatus.PENDING);
        booking.setPayByDeadline(LocalDateTime.now().plusHours(deadlineHoursFor(hospital)));
        return toSummary(bookingRepository.save(booking));
    }

    /**
     * Move a booking to another slot. Rules (reschedule-surcharge contract):
     * <ul>
     *   <li>Moving EARLIER costs a one-off GH¢20 surcharge
     *       ({@link PaymentService#RESCHEDULE_SURCHARGE}): the first such move
     *       with nothing paid throws {@link SurchargeRequiredException} (402)
     *       BEFORE any slot work — pay via
     *       POST /api/bookings/{id}/reschedule/surcharge, then retry. A paid
     *       surcharge is cleared once the earlier move completes, so the next
     *       earlier move charges again.</li>
     *   <li>A booking in active consultation (IN_CONSULTATION queue row)
     *       cannot move at all (409).</li>
     *   <li>Moving a checked-in / WAITING-queued booking cancels its live
     *       ticket and clears the check-in state, so the patient can check in
     *       again on the new slot.</li>
     * </ul>
     */
    @Transactional
    public BookingSummaryResponse reschedule(Long bookingId, Long patientId, RescheduleRequest req) {
        Booking booking = requireOwnActiveBooking(bookingId, patientId);
        LocalDate date = parseDate(req == null ? null : req.newDate(), "newDate");
        LocalTime time = parseTime(req == null ? null : req.newTime(), "newTime");

        TimeSlot old = booking.getTimeSlot();
        if (old != null && date.equals(old.getDate()) && time.equals(old.getStartTime())) {
            // Same-slot no-op: just refresh the pay-by deadline (unchanged).
            booking.setPayByDeadline(LocalDateTime.now().plusHours(deadlineHoursFor(booking.getHospital())));
            return toSummary(bookingRepository.save(booking));
        }

        // Earlier-than-original move (slot start times are LocalTime on a date).
        LocalDateTime original = old != null && old.getStartTime() != null && old.getDate() != null
                ? old.getDate().atTime(old.getStartTime())
                : null;
        boolean earlier = original != null && date.atTime(time).isBefore(original);

        // Consultation is a hard blocker — the patient is with the clinician now.
        QueueEntry queueRow = queueEntryRepository.findByBookingId(booking.getId()).orElse(null);
        if (queueRow != null && queueRow.getStatus() == QueueStatus.IN_CONSULTATION) {
            throw new ConflictException("You are being attended to now and cannot reschedule.");
        }

        // Unpaid earlier move → 402 before assertSlotBookable/claimSlot, so the
        // failed request changes no booking/queue/slot state.
        if (earlier && booking.getRescheduleSurchargePaidAt() == null) {
            throw new SurchargeRequiredException(PaymentService.RESCHEDULE_SURCHARGE,
                    "Moving your appointment earlier attracts a GH¢ 20 surcharge. "
                            + "Pay it, then retry this request.");
        }

        boolean resetQueuedState = Boolean.TRUE.equals(booking.getCheckedIn())
                || (queueRow != null && queueRow.getStatus() == QueueStatus.WAITING);

        assertSlotBookable(booking.getDepartment().getId(), date, time, booking.getId());
        Doctor doctor = pickDoctor(booking.getDepartment().getId(), date, time);
        TimeSlot next = claimSlot(doctor, date, time);

        if (old != null) {
            old.setBooked(false);
            timeSlotRepository.save(old);
        }
        booking.setTimeSlot(next);
        booking.setDoctor(doctor);
        booking.setPayByDeadline(LocalDateTime.now().plusHours(deadlineHoursFor(booking.getHospital())));
        if (earlier) {
            // One paid surcharge covers one earlier move — clear it so the next
            // earlier move must pay again.
            booking.setRescheduleSurchargePaidAt(null);
        }
        if (queueRow != null && queueRow.getStatus() == QueueStatus.WAITING) {
            queueRow.setStatus(QueueStatus.CANCELLED);
            queueEntryRepository.save(queueRow);
        }
        if (resetQueuedState) {
            booking.setCheckedIn(false);
            booking.setCheckInTime(null);
            booking.setAppointmentStatus(null);
        }
        Booking saved = bookingRepository.save(booking);
        notifyRescheduled(saved, date, time);
        return toSummary(saved);
    }

    @Transactional
    public List<BookingSummaryResponse> listOutstanding(Long patientId) {
        expireOverdueUnpaidBookings();
        return bookingRepository.findOutstandingForPatient(patientId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public int expireOverdueUnpaidBookings() {
        List<Booking> expired = bookingRepository.findExpiredUnpaid(LocalDateTime.now());
        int n = 0;
        for (Booking booking : expired) {
            TimeSlot slot = booking.getTimeSlot();
            if (slot != null && slot.isBooked()) {
                slot.setBooked(false);
                timeSlotRepository.save(slot);
            }
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);
            n++;
        }
        return n;
    }

    @Transactional
    public BookingSummaryResponse setPayByDeadline(Long bookingId, LocalDateTime deadline) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        if (deadline == null) {
            throw new IllegalArgumentException("payByDeadline must be an ISO datetime, e.g. 2026-08-20T09:00:00.");
        }
        booking.setPayByDeadline(deadline);
        return toSummary(bookingRepository.save(booking));
    }

    private void assertSlotBookable(Long departmentId, LocalDate date, LocalTime time, Long ignoreBookingId) {
        String dateStr = date.toString();
        String timeStr = isoTime(time);
        AvailabilityResponse avail = mobileDiscoveryService.getAvailability(departmentId, dateStr, 1);
        if (avail.closedDates().contains(dateStr)) {
            throw new ConflictException(
                    "The hospital is closed on " + dateStr + " — pick another date.");
        }
        if (avail.fullDates().contains(dateStr)) {
            throw new ConflictException(
                    "That day is fully booked — pick another date.");
        }
        DaySlots day = avail.slots().get(dateStr);
        if (day == null) {
            throw new ConflictException(
                    "That slot is no longer available — pick another time.");
        }
        List<SlotItem> all = new ArrayList<>();
        if (day.MORNING() != null) all.addAll(day.MORNING());
        if (day.AFTERNOON() != null) all.addAll(day.AFTERNOON());
        SlotItem match = all.stream()
                .filter(s -> timeStr.equals(normalizeTime(s.time())))
                .findFirst()
                .orElse(null);
        if (match == null) {
            throw new IllegalArgumentException(
                    "time must be a slot from GET /api/mobile/departments/" + departmentId
                            + "/availability (ISO LocalTime, e.g. 09:00).");
        }
        if (!match.available() && !isOwnSlot(ignoreBookingId, date, time)) {
            throw new ConflictException(
                    "That slot is no longer available — pick another time.");
        }
    }

    private boolean isOwnSlot(Long bookingId, LocalDate date, LocalTime time) {
        if (bookingId == null) return false;
        return bookingRepository.findById(bookingId)
                .map(Booking::getTimeSlot)
                .filter(s -> date.equals(s.getDate()) && time.equals(s.getStartTime()))
                .isPresent();
    }

    private Doctor pickDoctor(Long departmentId, LocalDate date, LocalTime time) {
        Long facilityId = departmentRepository.findById(departmentId)
                .map(com.example.demo.model.Department::getFacilityId)
                .orElse(null);
        List<Doctor> candidates = OnlineBookingSupport.staffLinkedDoctors(
                doctorRepository.findByDepartmentId(departmentId), staffMemberRepository, facilityId);
        if (candidates.isEmpty()) {
            throw new ConflictException(
                    "No doctors are currently available for online booking in this department.");
        }
        Doctor best = null;
        long bestCount = Long.MAX_VALUE;
        for (Doctor d : candidates) {
            boolean busy = timeSlotRepository.findByDoctorIdAndDateAndStartTime(d.getId(), date, time)
                    .stream().anyMatch(TimeSlot::isBooked);
            if (busy) continue;
            long count = bookingRepository.countByDoctorIdAndTimeSlot_DateAndStatusNot(
                    d.getId(), date, BookingStatus.CANCELLED);
            if (best == null || count < bestCount || (count == bestCount && d.getId() < best.getId())) {
                best = d;
                bestCount = count;
            }
        }
        if (best == null) {
            throw new ConflictException(
                    "That slot is no longer available — pick another time.");
        }
        return best;
    }

    private TimeSlot claimSlot(Doctor doctor, LocalDate date, LocalTime time) {
        int duration = doctor.getConsultationDuration() != null && doctor.getConsultationDuration() > 0
                ? doctor.getConsultationDuration() : 20;
        for (TimeSlot existing : timeSlotRepository.findByDoctorIdAndDateAndStartTime(doctor.getId(), date, time)) {
            if (existing.isBooked()) {
                throw new ConflictException(
                        "That slot is no longer available — pick another time.");
            }
            // Cancelled bookings still point at the row (OneToOne unique) —
            // only reuse the slot if nothing references it.
            if (!bookingRepository.existsByTimeSlotId(existing.getId())) {
                existing.setBooked(true);
                return timeSlotRepository.save(existing);
            }
        }
        TimeSlot created = new TimeSlot(doctor, date, time, time.plusMinutes(duration));
        created.setBooked(true);
        return timeSlotRepository.save(created);
    }

    /** Patient + front-desk notifications after a successful move. */
    private void notifyRescheduled(Booking booking, LocalDate date, LocalTime time) {
        String dept = booking.getDepartment() != null ? booking.getDepartment().getName() : "—";
        String body = String.format("APT-%04d", booking.getId()) + " · " + dept
                + " · " + date + " " + isoTime(time);
        if (booking.getPatient() != null) {
            patientNotificationService.create(booking.getPatient().getId(),
                    "appointment", "Appointment rescheduled", body, null);
        }
        notificationService.notifyQueueStaff(facilityIdOf(booking), "appointment",
                "Appointment rescheduled", body, null);
    }

    /** Patient in-app notification when their booking is cancelled (self or admin). */
    private void notifyCancelled(Booking booking) {
        if (booking.getPatient() == null) return;
        LocalDate date = booking.getTimeSlot() != null && booking.getTimeSlot().getDate() != null
                ? booking.getTimeSlot().getDate()
                : (booking.getBookingDate() != null ? booking.getBookingDate().toLocalDate() : null);
        String dept = booking.getDepartment() != null ? booking.getDepartment().getName() : "—";
        String body = String.format("APT-%04d", booking.getId()) + " · " + dept
                + (date != null ? " · " + date : "") + " was cancelled.";
        patientNotificationService.create(booking.getPatient().getId(),
                "appointment", "Booking cancelled", body, null);
    }

    private static Long facilityIdOf(Booking booking) {
        Department dept = booking.getDepartment();
        if (dept != null && dept.getFacilityId() != null) return dept.getFacilityId();
        return booking.getHospital() != null ? booking.getHospital().getId() : null;
    }

    private Booking requireOwnActiveBooking(Long bookingId, Long patientId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        if (!booking.getPatient().getId().equals(patientId)) {
            throw new AccessDeniedException("You can only change your own bookings.");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("This booking is cancelled and cannot be rescheduled.");
        }
        return booking;
    }

    private Hospital resolveHospital(Department dept) {
        if (dept.getHospital() != null) return dept.getHospital();
        if (dept.getFacilityId() != null) {
            return hospitalRepository.findById(dept.getFacilityId())
                    .orElseThrow(() -> new IllegalArgumentException("Hospital not found for this department."));
        }
        throw new IllegalArgumentException("Department is not attached to a hospital.");
    }

    private int deadlineHoursFor(Hospital hospital) {
        if (hospital == null) return DEFAULT_DEADLINE_HOURS;
        return operationalSettingsRepository.findByFacilityId(hospital.getId())
                .map(s -> s.getPayByDeadlineHours() > 0 ? s.getPayByDeadlineHours() : DEFAULT_DEADLINE_HOURS)
                .orElse(DEFAULT_DEADLINE_HOURS);
    }

    private BookingSummaryResponse toSummary(Booking booking) {
        TimeSlot slot = booking.getTimeSlot();
        String date = slot != null && slot.getDate() != null ? slot.getDate().toString() : null;
        String start = slot != null && slot.getStartTime() != null ? isoTime(slot.getStartTime()) : null;
        String deadline = booking.getPayByDeadline() != null
                ? booking.getPayByDeadline().toString() : null;
        return new BookingSummaryResponse(
                String.valueOf(booking.getId()),
                booking.getHospital() != null ? booking.getHospital().getName() : null,
                booking.getDepartment() != null ? booking.getDepartment().getName() : null,
                date,
                booking.getAmountDue(),
                deadline,
                booking.getStatus() != null ? booking.getStatus().name() : null,
                wirePaymentStatus(booking.getPaymentStatus()),
                start);
    }

    private static String wirePaymentStatus(PaymentStatus p) {
        if (p == null || p == PaymentStatus.PENDING) return "UNPAID";
        return p.name();
    }

    private static LocalDate parseDate(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " must be an ISO date (yyyy-MM-dd).");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " must be an ISO date (yyyy-MM-dd).");
        }
    }

    private static LocalTime parseTime(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " must be an ISO time (HH:mm), e.g. 09:00.");
        }
        try {
            String t = normalizeTime(raw.trim());
            return LocalTime.parse(t.length() == 5 ? t + ":00" : t);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " must be an ISO time (HH:mm), e.g. 09:00.");
        }
    }

    private static String normalizeTime(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }

    private static String isoTime(LocalTime t) {
        return t.toString().substring(0, 5);
    }

    private BookingResponse toResponse(Booking booking) {
        String patientName = booking.getPatient().getFirstName() + " " + booking.getPatient().getLastName();
        String doctorName = "Dr. " + booking.getDoctor().getFirstName() + " " + booking.getDoctor().getLastName();
        return new BookingResponse(
                booking.getId(),
                patientName,
                doctorName,
                booking.getDepartment().getName(),
                booking.getHospital().getName(),
                booking.getTimeSlot().getDate(),
                booking.getTimeSlot().getStartTime(),
                booking.getTimeSlot().getEndTime(),
                booking.getBookingDate(),
                booking.getStatus().name(),
                booking.getPaymentStatus().name(),
                booking.getAmountDue()
        );
    }
}
