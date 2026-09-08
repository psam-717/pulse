package com.example.demo.service;

import com.example.demo.dto.analytics.AnalyticsTotalsResponse;
import com.example.demo.dto.analytics.AppointmentStatusBreakdownResponse;
import com.example.demo.dto.analytics.DailyMetricResponse;
import com.example.demo.dto.analytics.DateRangeResponse;
import com.example.demo.dto.analytics.DepartmentAnalyticsResponse;
import com.example.demo.dto.analytics.FacilityAnalyticsResponse;
import com.example.demo.model.Booking;
import com.example.demo.model.Department;
import com.example.demo.model.PatientSource;
import com.example.demo.model.QueueEntry;
import com.example.demo.model.QueueStatus;
import com.example.demo.repository.BookingRepository;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facility analytics (BACKEND_SPEC §5.10/§6.9). Real aggregations over
 * Booking + QueueEntry — mock ratio math (§7.7) is NOT replicated; genuine
 * status counts and true served-visit waits replace it (product decisions,
 * Sep 2026).
 *
 * v1 definitions (documented):
 * - appointments/day  = bookings whose time-slot date falls in the range
 * - noShows/day       = bookings with facility status no_show that day
 * - walkIns/day       = queue entries source=WALK_IN, checkInAt that day
 * - served/day        = queue entries called into consultation that day
 *                       (status COMPLETED or IN_CONSULTATION)
 * - avg/p90 wait      = true percentiles of (calledAt − checkInAt) over the
 *                       served entries of that period/day
 * - noShowRate        = noShows / max(1, appointments) × 100
 * - capacityPerDay    = department.rooms × 10 (documented assumption — the
 *                       backend has no capacity model, spec §7.2/§10.8)
 * - utilization       = served / (capacityPerDay × days) clamped to 100
 */
@Service
public class AnalyticsService {

    private static final int MAX_RANGE_DAYS = 400;
    private static final int ASSUMED_VISITS_PER_ROOM_PER_DAY = 10;

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "scheduled", "Scheduled",
            "confirmed", "Confirmed",
            "checked_in", "Checked in",
            "completed", "Completed",
            "cancelled", "Cancelled",
            "no_show", "No-show");

    private final BookingRepository bookingRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final DepartmentRepository departmentRepository;

    public AnalyticsService(BookingRepository bookingRepository,
                            QueueEntryRepository queueEntryRepository,
                            DepartmentRepository departmentRepository) {
        this.bookingRepository = bookingRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.departmentRepository = departmentRepository;
    }

    public FacilityAnalyticsResponse analytics(Long facilityId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be on or before 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Range too wide — maximum " + MAX_RANGE_DAYS + " days");
        }

        List<Department> depts = departmentRepository.findByFacilityId(facilityId);
        List<String> deptIds = depts.stream().map(d -> String.valueOf(d.getId())).toList();
        List<QueueEntry> allEntries = queueEntryRepository.findByDepartmentIdIn(deptIds);

        List<Booking> currentBookings = facilityBookings(facilityId, from, to);
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = from.minusDays(ChronoUnit.DAYS.between(from, to) + 1);
        List<Booking> previousBookings = facilityBookings(facilityId, prevFrom, prevTo);

        List<DailyMetricResponse> facilityDaily = dailyRows(currentBookings, allEntries, from, to);
        AnalyticsTotalsResponse totals = totals(currentBookings, allEntries, from, to);
        AnalyticsTotalsResponse previousTotals = totals(previousBookings, allEntries, prevFrom, prevTo);

        List<DepartmentAnalyticsResponse> departmentRows = new ArrayList<>();
        for (Department d : depts) {
            String deptId = String.valueOf(d.getId());
            List<QueueEntry> deptEntries = allEntries.stream()
                    .filter(e -> deptId.equals(e.getDepartmentId()))
                    .toList();
            List<Booking> deptCurrent = currentBookings.stream()
                    .filter(b -> b.getDepartment() != null && deptId.equals(String.valueOf(b.getDepartment().getId())))
                    .toList();
            List<Booking> deptPrevious = previousBookings.stream()
                    .filter(b -> b.getDepartment() != null && deptId.equals(String.valueOf(b.getDepartment().getId())))
                    .toList();

            List<DailyMetricResponse> deptDaily = dailyRows(deptCurrent, deptEntries, from, to);
            AnalyticsTotalsResponse deptTotals = totals(deptCurrent, deptEntries, from, to);
            AnalyticsTotalsResponse deptPreviousTotals = totals(deptPrevious, deptEntries, prevFrom, prevTo);

            int rooms = d.getRooms() == null ? 0 : d.getRooms();
            int capacityPerDay = rooms * ASSUMED_VISITS_PER_ROOM_PER_DAY;
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            int capacityTotal = capacityPerDay * (int) days;
            int utilization = capacityTotal <= 0 ? 0
                    : (int) Math.min(100, Math.round(deptTotals.served() * 100.0 / capacityTotal));

            departmentRows.add(new DepartmentAnalyticsResponse(
                    deptId, d.getName(), deptDaily, deptTotals, deptPreviousTotals,
                    capacityPerDay, utilization));
        }
        departmentRows.sort(Comparator.comparing(DepartmentAnalyticsResponse::departmentName));

        return new FacilityAnalyticsResponse(
                new DateRangeResponse(from.toString(), to.toString()),
                facilityDaily,
                totals,
                previousTotals,
                breakdown(currentBookings),
                departmentRows);
    }

    // ===================== aggregations =====================

    private List<DailyMetricResponse> dailyRows(List<Booking> bookings, List<QueueEntry> entries,
                                                LocalDate from, LocalDate to) {
        List<DailyMetricResponse> rows = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate day = cursor;
            List<Booking> dayBookings = bookings.stream()
                    .filter(b -> b.getTimeSlot() != null && b.getTimeSlot().getDate() != null)
                    .filter(b -> b.getTimeSlot().getDate().equals(day))
                    .toList();
            int appointments = dayBookings.size();
            int noShows = (int) dayBookings.stream()
                    .filter(b -> "no_show".equals(appointmentStatus(b))).count();

            List<QueueEntry> checkedInThatDay = entries.stream()
                    .filter(e -> e.getCheckInAt() != null && e.getCheckInAt().toLocalDate().equals(day))
                    .toList();
            int walkIns = (int) checkedInThatDay.stream()
                    .filter(e -> e.getSource() == PatientSource.WALK_IN)
                    .count();

            List<QueueEntry> servedThatDay = entries.stream()
                    .filter(e -> e.getCalledAt() != null && e.getCalledAt().toLocalDate().equals(day))
                    .filter(e -> e.getStatus() == QueueStatus.COMPLETED
                            || e.getStatus() == QueueStatus.IN_CONSULTATION)
                    .toList();
            int served = servedThatDay.size();
            List<Long> waits = waitMinutes(servedThatDay);

            rows.add(new DailyMetricResponse(
                    day.toString(),
                    appointments,
                    walkIns,
                    appointments + walkIns,
                    (int) Math.round(average(waits)),
                    p90(waits),
                    served,
                    noShows,
                    appointments == 0 ? 0 : noShows * 100.0 / appointments));
            cursor = cursor.plusDays(1);
        }
        return rows;
    }

    private AnalyticsTotalsResponse totals(List<Booking> bookings, List<QueueEntry> entries,
                                           LocalDate from, LocalDate to) {
        List<Booking> inRange = bookings.stream()
                .filter(b -> b.getTimeSlot() != null && b.getTimeSlot().getDate() != null)
                .filter(b -> !b.getTimeSlot().getDate().isBefore(from)
                        && !b.getTimeSlot().getDate().isAfter(to))
                .toList();
        int appointments = inRange.size();
        int noShows = (int) inRange.stream()
                .filter(b -> "no_show".equals(appointmentStatus(b))).count();

        List<QueueEntry> checkedInInRange = entries.stream()
                .filter(e -> e.getCheckInAt() != null
                        && !e.getCheckInAt().toLocalDate().isBefore(from)
                        && !e.getCheckInAt().toLocalDate().isAfter(to))
                .toList();
        int walkIns = (int) checkedInInRange.stream()
                .filter(e -> e.getSource() == PatientSource.WALK_IN)
                .count();

        List<QueueEntry> servedInRange = entries.stream()
                .filter(e -> e.getCalledAt() != null
                        && !e.getCalledAt().toLocalDate().isBefore(from)
                        && !e.getCalledAt().toLocalDate().isAfter(to))
                .filter(e -> e.getStatus() == QueueStatus.COMPLETED
                        || e.getStatus() == QueueStatus.IN_CONSULTATION)
                .toList();
        int served = servedInRange.size();
        List<Long> waits = waitMinutes(servedInRange);

        return new AnalyticsTotalsResponse(
                appointments + walkIns,
                (int) Math.round(average(waits)),
                p90(waits),
                served,
                appointments == 0 ? 0 : noShows * 100.0 / appointments);
    }

    private List<AppointmentStatusBreakdownResponse> breakdown(List<Booking> bookings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String s : List.of("completed", "confirmed", "checked_in", "scheduled", "cancelled", "no_show")) {
            counts.put(s, 0);
        }
        for (Booking b : bookings) {
            counts.merge(appointmentStatus(b), 1, Integer::sum);
        }
        List<AppointmentStatusBreakdownResponse> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > 0 || true) { // include zero rows so the client chart is stable
                out.add(new AppointmentStatusBreakdownResponse(
                        e.getKey(), STATUS_LABELS.getOrDefault(e.getKey(), e.getKey()), e.getValue()));
            }
        }
        return out;
    }

    // ===================== helpers =====================

    private List<Booking> facilityBookings(Long facilityId, LocalDate from, LocalDate to) {
        return bookingRepository.findByTimeSlot_DateBetween(from, to).stream()
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

    private static List<Long> waitMinutes(List<QueueEntry> served) {
        return served.stream()
                .filter(e -> e.getCheckInAt() != null)
                .map(e -> Duration.between(e.getCheckInAt(), e.getCalledAt()).toMinutes())
                .filter(m -> m >= 0)
                .sorted()
                .toList();
    }

    private static double average(List<Long> values) {
        if (values.isEmpty()) return 0;
        long sum = values.stream().reduce(0L, Long::sum);
        return (double) sum / values.size();
    }

    private static int p90(List<Long> sorted) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(0.9 * sorted.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sorted.size()) idx = sorted.size() - 1;
        return (int) Math.round(sorted.get(idx));
    }
}
