package com.example.demo.service;

import com.example.demo.dto.AvailabilityResponse;
import com.example.demo.dto.AvailabilityResponse.DaySlots;
import com.example.demo.dto.AvailabilityResponse.SlotItem;
import com.example.demo.dto.DepartmentOptionResponse;
import com.example.demo.dto.HospitalCardResponse;
import com.example.demo.model.Department;
import com.example.demo.model.Doctor;
import com.example.demo.model.Hospital;
import com.example.demo.model.QueueStatus;
import com.example.demo.model.TimeSlot;
import com.example.demo.model.VerificationStatus;
import com.example.demo.model.WorkingHours;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.DoctorRepository;
import com.example.demo.repository.HospitalRepository;
import com.example.demo.repository.QueueEntryRepository;
import com.example.demo.repository.TimeSlotRepository;
import com.example.demo.repository.WorkingHoursRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mobile hospital discovery + department availability (ARCHITECTURE.md §8 P2 / G4, G5).
 * Returns the mobile-store shapes verbatim — no wrappers, no renames.
 */
@Service
public class MobileDiscoveryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalTime NOON = LocalTime.NOON;
    private static final LocalTime DEFAULT_OPEN = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_CLOSE = LocalTime.of(17, 0);
    private static final int DEFAULT_DURATION_MIN = 20;
    private static final double DEFAULT_RATING = 4.5;
    private static final String DEFAULT_REVIEWS = "—";
    private static final double DEFAULT_DISTANCE_KM = 2.5;
    private static final int EARTH_RADIUS_KM = 6371;

    private final HospitalRepository hospitalRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final QueueEntryRepository queueEntryRepository;

    public MobileDiscoveryService(HospitalRepository hospitalRepository,
                                  DepartmentRepository departmentRepository,
                                  DoctorRepository doctorRepository,
                                  TimeSlotRepository timeSlotRepository,
                                  WorkingHoursRepository workingHoursRepository,
                                  QueueEntryRepository queueEntryRepository) {
        this.hospitalRepository = hospitalRepository;
        this.departmentRepository = departmentRepository;
        this.doctorRepository = doctorRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.queueEntryRepository = queueEntryRepository;
    }

    public List<HospitalCardResponse> listHospitals(Double lat, Double lng) {
        List<Hospital> approved = hospitalRepository.findByVerificationStatus(VerificationStatus.APPROVED);
        return approved.stream()
                .sorted(Comparator.comparing(Hospital::getName, String.CASE_INSENSITIVE_ORDER))
                .map(h -> toCard(h, lat, lng))
                .toList();
    }

    public List<DepartmentOptionResponse> listDepartments(Long hospitalId) {
        requireApprovedHospital(hospitalId);
        return departmentsForHospital(hospitalId).stream()
                .filter(d -> d.getStatus() == null || !"archived".equalsIgnoreCase(d.getStatus()))
                .sorted(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
                .map(d -> new DepartmentOptionResponse(
                        d.getId(),
                        d.getName(),
                        d.getConsultationFee(),
                        d.getDescription()))
                .toList();
    }

    public AvailabilityResponse getAvailability(Long departmentId, String fromRaw, Integer daysRaw) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Department not found. Use GET /api/mobile/hospitals/{id}/departments for valid ids."));

        Long hospitalId = resolveHospitalId(dept);
        if (hospitalId == null) {
            throw new IllegalArgumentException("Department is not attached to a hospital.");
        }
        requireApprovedHospital(hospitalId);

        LocalDate from = parseFrom(fromRaw);
        int days = daysRaw == null ? 14 : daysRaw;
        if (days < 1 || days > 31) {
            throw new IllegalArgumentException("days must be between 1 and 31.");
        }
        LocalDate to = from.plusDays(days - 1L);

        Map<Integer, WorkingHours> hoursByDow = workingHoursRepository.findByHospitalId(hospitalId)
                .stream()
                .collect(Collectors.toMap(WorkingHours::getDayOfWeek, wh -> wh, (a, b) -> a));
        boolean hasConfiguredHours = !hoursByDow.isEmpty();

        List<Doctor> doctors = doctorRepository.findByDepartmentId(departmentId);
        int duration = doctors.stream()
                .map(Doctor::getConsultationDuration)
                .filter(d -> d != null && d > 0)
                .findFirst()
                .orElse(DEFAULT_DURATION_MIN);

        List<Long> doctorIds = doctors.stream().map(Doctor::getId).toList();
        List<TimeSlot> existing = doctorIds.isEmpty()
                ? List.of()
                : timeSlotRepository.findByDoctorIdInAndDateBetween(doctorIds, from, to);

        Map<LocalDate, Map<LocalTime, long[]>> occupancy = new LinkedHashMap<>();
        for (TimeSlot slot : existing) {
            Map<LocalTime, long[]> byTime = occupancy.computeIfAbsent(slot.getDate(), d -> new LinkedHashMap<>());
            long[] counts = byTime.computeIfAbsent(slot.getStartTime(), t -> new long[2]);
            counts[0]++; // present
            if (slot.isBooked()) counts[1]++; // booked
        }
        int doctorCount = doctors.size();

        List<String> closedDates = new ArrayList<>();
        List<String> fullDates = new ArrayList<>();
        Map<String, DaySlots> slots = new LinkedHashMap<>();

        for (int i = 0; i < days; i++) {
            LocalDate date = from.plusDays(i);
            String dateKey = date.toString();
            int dow = date.getDayOfWeek().getValue(); // 1=Mon .. 7=Sun

            LocalTime open = DEFAULT_OPEN;
            LocalTime close = DEFAULT_CLOSE;
            boolean closed = !hasConfiguredHours && dow == 7;
            if (hasConfiguredHours) {
                WorkingHours wh = hoursByDow.get(dow);
                if (wh == null || wh.isClosed()) {
                    closed = true;
                } else {
                    open = wh.getOpenTime() != null ? wh.getOpenTime() : DEFAULT_OPEN;
                    close = wh.getCloseTime() != null ? wh.getCloseTime() : DEFAULT_CLOSE;
                }
            } else {
                open = parseDeptTime(dept.getOpensAt(), DEFAULT_OPEN);
                close = parseDeptTime(dept.getClosesAt(), DEFAULT_CLOSE);
            }

            if (closed) {
                closedDates.add(dateKey);
                continue;
            }

            List<SlotItem> morning = new ArrayList<>();
            List<SlotItem> afternoon = new ArrayList<>();
            Map<LocalTime, long[]> dayOcc = occupancy.getOrDefault(date, Map.of());

            for (LocalTime t = open; !t.plusMinutes(duration).isAfter(close); t = t.plusMinutes(duration)) {
                boolean available = isAvailable(t, dayOcc, doctorCount);
                SlotItem item = new SlotItem(t.toString().substring(0, 5), available);
                if (!t.isAfter(NOON)) {
                    morning.add(item);
                } else {
                    afternoon.add(item);
                }
            }

            boolean anySlot = !morning.isEmpty() || !afternoon.isEmpty();
            boolean anyFree = morning.stream().anyMatch(SlotItem::available)
                    || afternoon.stream().anyMatch(SlotItem::available);
            if (anySlot && !anyFree) {
                fullDates.add(dateKey);
                continue;
            }

            slots.put(dateKey, new DaySlots(morning, afternoon));
        }

        return new AvailabilityResponse(closedDates, fullDates, slots);
    }

    private static boolean isAvailable(LocalTime t, Map<LocalTime, long[]> dayOcc, int doctorCount) {
        if (doctorCount <= 0) return true;
        long[] counts = dayOcc.get(t);
        if (counts == null) return true; // no TimeSlot rows → treat as free
        return counts[1] < doctorCount;
    }

    private Hospital requireApprovedHospital(Long hospitalId) {
        Hospital h = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hospital not found. Use GET /api/mobile/hospitals for approved facilities."));
        if (h.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new IllegalArgumentException(
                    "Hospital is not approved for discovery yet.");
        }
        return h;
    }

    private List<Department> departmentsForHospital(Long hospitalId) {
        Set<Long> seen = new LinkedHashSet<>();
        List<Department> out = new ArrayList<>();
        for (Department d : departmentRepository.findByHospitalId(hospitalId)) {
            if (seen.add(d.getId())) out.add(d);
        }
        for (Department d : departmentRepository.findByFacilityId(hospitalId)) {
            if (seen.add(d.getId())) out.add(d);
        }
        return out;
    }

    private static Long resolveHospitalId(Department dept) {
        if (dept.getHospital() != null) return dept.getHospital().getId();
        return dept.getFacilityId();
    }

    private HospitalCardResponse toCard(Hospital h, Double lat, Double lng) {
        List<Department> depts = departmentsForHospital(h.getId());
        return new HospitalCardResponse(
                String.valueOf(h.getId()),
                h.getName(),
                h.getAddress(),
                DEFAULT_RATING,
                DEFAULT_REVIEWS,
                h.getLogoUrl(),
                distanceKm(h, lat, lng),
                waitTime(depts),
                statusLabel(h, depts),
                specialtiesOf(h, depts));
    }

    private String waitTime(List<Department> depts) {
        long waiting = 0;
        for (Department d : depts) {
            waiting += queueEntryRepository.countByDepartmentIdAndStatus(
                    String.valueOf(d.getId()), QueueStatus.WAITING);
        }
        if (waiting <= 3) return "Low";
        if (waiting <= 8) return "Moderate";
        return "High";
    }

    private String statusLabel(Hospital h, List<Department> depts) {
        boolean twentyFour = depts.stream().anyMatch(d -> Boolean.TRUE.equals(d.getTwentyFourSeven()));
        if (twentyFour) return "Open 24/7";

        int dow = LocalDate.now().getDayOfWeek().getValue();
        List<WorkingHours> hours = workingHoursRepository.findByHospitalId(h.getId());
        if (hours.isEmpty()) {
            return dow == 7 ? "Closed" : "Open";
        }
        return hours.stream()
                .filter(wh -> dow == wh.getDayOfWeek())
                .findFirst()
                .map(wh -> {
                    if (wh.isClosed()) return "Closed";
                    if (wh.getCloseTime() != null) return "Open until " + wh.getCloseTime().toString().substring(0, 5);
                    return "Open";
                })
                .orElse("Closed");
    }

    private static List<String> specialtiesOf(Hospital h, List<Department> depts) {
        String raw = h.getSpecialties();
        if (raw != null && !raw.isBlank()) {
            try {
                JsonNode n = MAPPER.readTree(raw);
                if (n != null && n.isArray()) {
                    List<String> list = new ArrayList<>();
                    n.forEach(item -> list.add(item.asText()));
                    if (!list.isEmpty()) return list;
                }
            } catch (Exception ignored) {
                // fall through to department names
            }
        }
        return depts.stream()
                .map(Department::getName)
                .distinct()
                .limit(6)
                .toList();
    }

    private static Double distanceKm(Hospital h, Double lat, Double lng) {
        if (lat == null || lng == null) return DEFAULT_DISTANCE_KM;
        if (h.getLatitude() == null || h.getLongitude() == null) return DEFAULT_DISTANCE_KM;
        double dLat = Math.toRadians(h.getLatitude() - lat);
        double dLng = Math.toRadians(h.getLongitude() - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(h.getLatitude()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_KM * c * 10.0) / 10.0;
    }

    private static LocalDate parseFrom(String fromRaw) {
        if (fromRaw == null || fromRaw.isBlank()) return LocalDate.now();
        try {
            return LocalDate.parse(fromRaw.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("from must be an ISO date (yyyy-MM-dd).");
        }
    }

    private static LocalTime parseDeptTime(String raw, LocalTime fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            return fallback;
        }
    }
}
