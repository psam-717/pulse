package com.example.demo.service;

import com.example.demo.dto.dashboard.DashboardAlertResponse;
import com.example.demo.dto.dashboard.DashboardDepartmentQueueResponse;
import com.example.demo.dto.dashboard.StatMetricResponse;
import com.example.demo.dto.dashboard.VolumePointResponse;
import com.example.demo.model.Booking;
import com.example.demo.model.Department;
import com.example.demo.model.Hospital;
import com.example.demo.model.PatientSource;
import com.example.demo.model.QueueEntry;
import com.example.demo.model.QueueStatus;
import com.example.demo.repository.BookingRepository;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.HospitalRepository;
import com.example.demo.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin dashboard reads (BACKEND_SPEC §5.9/§6.8). All four endpoints are
 * genuine server-side aggregations over Booking + QueueEntry — the mock
 * literals were hardcoded with no derivation logic (§7.8), so the formulas
 * below are new and documented. Trends compare today vs yesterday (product
 * decision, Sep 2026).
 *
 * Metric definitions (v1, documented):
 * - patients-in-queue      = WAITING + IN_CONSULTATION right now
 * - avg-wait-time          = mean elapsed minutes of current WAITING entries
 * - appointments-today     = bookings whose time-slot date is today
 * - no-show-rate           = no_show / (total − cancelled) × 100, today
 * - yesterday baselines    = same metric computed for yesterday (queue
 *                            yesterday = entries that checked in yesterday)
 */
@Service
public class DashboardService {

    /** Live Queue severity thresholds — shared with QueueService (§7.2). */
    private static final int CRITICAL_WAIT_MINUTES = 40;
    private static final int WARNING_WAIT_MINUTES = 25;

    private final DepartmentRepository departmentRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final BookingRepository bookingRepository;
    private final HospitalRepository hospitalRepository;

    public DashboardService(DepartmentRepository departmentRepository,
                            QueueEntryRepository queueEntryRepository,
                            BookingRepository bookingRepository,
                            HospitalRepository hospitalRepository) {
        this.departmentRepository = departmentRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.bookingRepository = bookingRepository;
        this.hospitalRepository = hospitalRepository;
    }

    // ===================== GET /dashboard/stats =====================

    public List<StatMetricResponse> stats(Long facilityId) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<Department> depts = departmentRepository.findByFacilityId(facilityId);
        List<String> deptIds = deptIds(depts);
        List<QueueEntry> entries = queueEntryRepository.findByDepartmentIdIn(deptIds);

        // --- patients in queue (live) ---
        int inQueue = (int) entries.stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING
                        || e.getStatus() == QueueStatus.IN_CONSULTATION)
                .count();
        int yesterdayArrivals = (int) entries.stream()
                .filter(e -> isSameDay(e.getCheckInAt(), yesterday))
                .count();

        // --- avg wait (live waiting entries vs yesterday's served waits) ---
        int avgWaitNow = avgMinutes(entries.stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING && e.getCheckInAt() != null)
                .map(QueueEntry::getCheckInAt)
                .map(ts -> Duration.between(ts, LocalDateTime.now()).toMinutes())
                .toList());
        int avgWaitYesterday = avgMinutes(servedWaitMinutes(entries, yesterday));

        // --- appointments today / yesterday (by time-slot date) ---
        List<Booking> todayBookings = bookingsOn(bookingRepository.findByTimeSlot_Date(today), facilityId);
        List<Booking> yesterdayBookings = bookingsOn(bookingRepository.findByTimeSlot_Date(yesterday), facilityId);
        int appointmentsToday = todayBookings.size();
        int appointmentsYesterday = yesterdayBookings.size();

        // --- no-show rate ---
        double noShowToday = noShowRate(todayBookings);
        double noShowYesterday = noShowRate(yesterdayBookings);

        List<StatMetricResponse> out = new ArrayList<>();
        out.add(metric("patients-in-queue", "Patients in Queue", String.valueOf(inQueue), null,
                inQueue - yesterdayArrivals, "count",
                delta -> delta > 0 ? "negative" : delta < 0 ? "positive" : "neutral"));
        out.add(metric("avg-wait-time", "Avg Wait Time", String.valueOf(avgWaitNow), "min",
                avgWaitNow - avgWaitYesterday, "minutes",
                delta -> delta > 0 ? "negative" : delta < 0 ? "positive" : "neutral"));
        out.add(metric("appointments-today", "Appointments Today", String.valueOf(appointmentsToday), null,
                appointmentsToday - appointmentsYesterday, "count",
                delta -> delta > 0 ? "positive" : delta < 0 ? "negative" : "neutral"));
        out.add(metric("no-show-rate", "No-show Rate", fmtRate(noShowToday), "%",
                (int) Math.round(noShowToday * 10) - (int) Math.round(noShowYesterday * 10), "tenthsOfPercent",
                delta -> delta > 0 ? "negative" : delta < 0 ? "positive" : "neutral"));
        return out;
    }

    private StatMetricResponse metric(String id, String label, String value, String unit,
                                      int rawDelta, String deltaKind,
                                      java.util.function.Function<Integer, String> sentimentFor) {
        int delta = rawDelta;
        String direction = delta > 0 ? "up" : "down";
        String trendLabel;
        switch (deltaKind) {
            case "minutes" -> trendLabel = (delta > 0 ? "+" : "") + delta + "m";
            case "tenthsOfPercent" -> {
                // delta was stored in tenths of a percent point
                double p = delta / 10.0;
                trendLabel = (p > 0 ? "+" : "") + p + "%";
            }
            default -> trendLabel = (delta > 0 ? "+" : "") + delta;
        }
        if (delta == 0) {
            trendLabel = "0";
        }
        return new StatMetricResponse(id, label, value, unit,
                new StatMetricResponse.StatTrendResponse(direction, trendLabel,
                        delta == 0 ? "neutral" : sentimentFor.apply(delta)));
    }

    // ===================== GET /dashboard/queue =====================

    /** Per-department summary for the admin overview queue widget. */
    public List<DashboardDepartmentQueueResponse> queue(Long facilityId) {
        List<Department> depts = departmentRepository.findByFacilityId(facilityId);
        List<QueueEntry> entries = queueEntryRepository.findByDepartmentIdIn(deptIds(depts));
        List<DashboardDepartmentQueueResponse> out = new ArrayList<>();
        for (Department d : depts) {
            String deptId = String.valueOf(d.getId());
            List<QueueEntry> deptEntries = entries.stream()
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
            String statusLabel = serving != null
                    ? "Serving #" + serving.getTicketNumber()
                    : waiting.isEmpty() ? "No patients" : "Patients waiting";
            String severity = longest > CRITICAL_WAIT_MINUTES ? "critical"
                    : longest > WARNING_WAIT_MINUTES ? "warning" : "ok";
            out.add(new DashboardDepartmentQueueResponse(deptId, d.getName(), statusLabel,
                    waiting.size(), longest, severity));
        }
        return out;
    }

    // ===================== GET /dashboard/alerts =====================

    /**
     * Derived alerts (v1 rules, all documented in code):
     * - critical/warning per department when longest wait crosses 40/25m
     * - info when tomorrow has unconfirmed (scheduled) appointments
     * - warning when the facility's HeFRA verification is not APPROVED
     */
    public List<DashboardAlertResponse> alerts(Long facilityId) {
        List<Department> depts = departmentRepository.findByFacilityId(facilityId);
        List<QueueEntry> entries = queueEntryRepository.findByDepartmentIdIn(deptIds(depts));
        List<DashboardAlertResponse> out = new ArrayList<>();

        for (Department d : depts) {
            String deptId = String.valueOf(d.getId());
            long longest = entries.stream()
                    .filter(e -> deptId.equals(e.getDepartmentId()))
                    .filter(e -> e.getStatus() == QueueStatus.WAITING && e.getCheckInAt() != null)
                    .mapToLong(e -> Duration.between(e.getCheckInAt(), LocalDateTime.now()).toMinutes())
                    .max().orElse(0);
            if (longest > CRITICAL_WAIT_MINUTES) {
                out.add(new DashboardAlertResponse("alert-wait-" + deptId, "critical",
                        d.getName() + " wait times high",
                        "Longest wait " + longest + "m — over the " + CRITICAL_WAIT_MINUTES + "m threshold"));
            } else if (longest > WARNING_WAIT_MINUTES) {
                out.add(new DashboardAlertResponse("alert-wait-" + deptId, "warning",
                        d.getName() + " wait times rising",
                        "Longest wait " + longest + "m — over the " + WARNING_WAIT_MINUTES + "m threshold"));
            }
        }

        long tomorrowScheduled = bookingsOn(
                bookingRepository.findByTimeSlot_Date(LocalDate.now().plusDays(1)), facilityId)
                .stream().filter(b -> "scheduled".equals(appointmentStatus(b))).count();
        if (tomorrowScheduled > 0) {
            out.add(new DashboardAlertResponse("alert-unconfirmed", "info",
                    tomorrowScheduled + " appointment" + (tomorrowScheduled == 1 ? "" : "s") + " unconfirmed",
                    "For tomorrow"));
        }

        hospitalRepository.findById(facilityId).ifPresent(h -> {
            String verification = String.valueOf(h.getVerificationStatus());
            if (!"APPROVED".equalsIgnoreCase(verification) && !"null".equalsIgnoreCase(verification)) {
                out.add(new DashboardAlertResponse("alert-hefra", "warning",
                        "HeFRA verification pending", "Requires admin review"));
            }
        });
        return out;
    }

    // ===================== GET /dashboard/patient-volume =====================

    /** Today's arrivals bucketed every 2 hours 8 AM – 6 PM (mirrors the mock grid). */
    public List<VolumePointResponse> patientVolume(Long facilityId) {
        List<Department> depts = departmentRepository.findByFacilityId(facilityId);
        List<QueueEntry> entries = queueEntryRepository.findByDepartmentIdIn(deptIds(depts));
        List<Booking> bookings = bookingsOn(bookingRepository.findByTimeSlot_Date(LocalDate.now()), facilityId);

        List<VolumePointResponse> out = new ArrayList<>();
        for (int h = 8; h <= 18; h += 2) {
            final int bucket = h;
            String label = hourLabel(bucket);
            int walkIns = (int) entries.stream()
                    .filter(e -> e.getSource() == PatientSource.WALK_IN && e.getCheckInAt() != null)
                    .filter(e -> sameHourBucket(e.getCheckInAt().getHour(), bucket))
                    .count();
            int appts = (int) bookings.stream()
                    .filter(b -> b.getTimeSlot() != null && b.getTimeSlot().getStartTime() != null)
                    .filter(b -> sameHourBucket(b.getTimeSlot().getStartTime().getHour(), bucket))
                    .count();
            out.add(new VolumePointResponse(label, walkIns, appts));
        }
        return out;
    }

    // ===================== helpers =====================

    private static List<String> deptIds(List<Department> depts) {
        return depts.stream().map(d -> String.valueOf(d.getId())).toList();
    }

    private static boolean isSameDay(LocalDateTime ts, LocalDate day) {
        return ts != null && ts.toLocalDate().equals(day);
    }

    private static int avgMinutes(List<Long> minutes) {
        if (minutes.isEmpty()) return 0;
        long sum = minutes.stream().filter(m -> m != null && m >= 0).reduce(0L, Long::sum);
        long counted = minutes.stream().filter(m -> m != null && m >= 0).count();
        return counted == 0 ? 0 : (int) Math.round((double) sum / counted);
    }

    /** Wait minutes (calledAt − checkInAt) of entries served on the given day. */
    private static List<Long> servedWaitMinutes(List<QueueEntry> entries, LocalDate day) {
        return entries.stream()
                .filter(e -> e.getCalledAt() != null && e.getCheckInAt() != null)
                .filter(e -> e.getCalledAt().toLocalDate().equals(day))
                .map(e -> Duration.between(e.getCheckInAt(), e.getCalledAt()).toMinutes())
                .filter(m -> m >= 0)
                .toList();
    }

    private static boolean sameHourBucket(int hour, int bucketStart) {
        return hour >= bucketStart && hour < bucketStart + 2;
    }

    private static String hourLabel(int hour) {
        int h12 = hour % 12 == 0 ? 12 : hour % 12;
        return h12 + (hour < 12 ? " AM" : " PM");
    }

    private List<Booking> bookingsOn(List<Booking> candidates, Long facilityId) {
        return candidates.stream()
                .filter(b -> belongsToFacility(b, facilityId))
                .toList();
    }

    private static boolean belongsToFacility(Booking b, Long facilityId) {
        Department d = b.getDepartment();
        Long deptFacility = d != null && d.getFacilityId() != null
                ? d.getFacilityId()
                : (b.getHospital() != null ? b.getHospital().getId() : null);
        return facilityId.equals(deptFacility);
    }

    /** Facility-plane appointment status — same rules as AppointmentService. */
    private static String appointmentStatus(Booking b) {
        if (b.getAppointmentStatus() != null) return b.getAppointmentStatus();
        if (Boolean.TRUE.equals(b.getCheckedIn())) return "checked_in";
        return switch (b.getStatus()) {
            case PENDING_PAYMENT -> "scheduled";
            case CONFIRMED -> "confirmed";
            case CANCELLED -> "cancelled";
        };
    }

    private static double noShowRate(List<Booking> bookings) {
        long cancellations = bookings.stream()
                .filter(b -> "cancelled".equals(appointmentStatus(b))).count();
        long noShows = bookings.stream()
                .filter(b -> "no_show".equals(appointmentStatus(b))).count();
        long denominator = bookings.size() - cancellations;
        return denominator <= 0 ? 0 : noShows * 100.0 / denominator;
    }

    private static String fmtRate(double rate) {
        return String.format(Locale.ROOT, "%.1f", rate);
    }
}
