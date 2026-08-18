package com.example.demo.service;

import com.example.demo.dto.CreateDepartmentRequest;
import com.example.demo.dto.DepartmentResponse;
import com.example.demo.dto.DepartmentStatsResponse;
import com.example.demo.dto.UpdateDepartmentRequest;
import com.example.demo.exception.ConflictException;
import com.example.demo.model.Booking;
import com.example.demo.model.BookingStatus;
import com.example.demo.model.Department;
import com.example.demo.model.StaffAccountStatus;
import com.example.demo.model.StaffDutyStatus;
import com.example.demo.model.StaffRole;
import com.example.demo.repository.BookingRepository;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.StaffMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Facility-plane department CRUD (BACKEND_SPEC.md §6.2) — every operation is
 * scoped to the caller's facilityId, taken from the JWT, never from the
 * client (§2.1). Derived fields (staffing counts, live floor activity) are
 * computed server-side (§10.2): doctorsOnDuty/totalDoctors come from the
 * Staff domain; waiting/inConsultation/avgWaitMinutes stay 0 until the Live
 * Queue domain lands (Phase 5); appointmentsToday comes from Bookings.
 */
@Service
public class DepartmentService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentService.class);
    private static final Set<String> VALID_STATUSES = Set.of("active", "closed", "archived");

    private final DepartmentRepository departmentRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final BookingRepository bookingRepository;
    private final com.example.demo.repository.HospitalRepository hospitalRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                             StaffMemberRepository staffMemberRepository,
                             BookingRepository bookingRepository,
                             com.example.demo.repository.HospitalRepository hospitalRepository) {
        this.departmentRepository = departmentRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.bookingRepository = bookingRepository;
        this.hospitalRepository = hospitalRepository;
    }

    // ===== Read =====

    public List<DepartmentResponse> list(Long facilityId) {
        return departmentRepository.findByFacilityId(facilityId).stream()
                .sorted(Comparator
                        .comparing((Department d) -> !"active".equals(statusOf(d))) // active first
                        .thenComparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    public DepartmentStatsResponse stats(Long facilityId) {
        List<Department> departments = departmentRepository.findByFacilityId(facilityId);
        int total = 0, active = 0, closed = 0, doctorsOnDuty = 0, rooms = 0, waiting = 0;
        for (Department d : departments) {
            total++;
            boolean isActive = "active".equals(statusOf(d));
            if (isActive) {
                active++;
                doctorsOnDuty += toResponse(d).doctorsOnDuty();
                waiting += 0; // queue domain lands in Phase 5
            } else {
                closed++;
            }
            rooms += d.getRooms() != null ? d.getRooms() : 0;
        }
        return new DepartmentStatsResponse(total, active, closed, doctorsOnDuty, rooms, waiting);
    }

    public DepartmentResponse get(Long facilityId, Long id) {
        return toResponse(findOwned(facilityId, id));
    }

    // ===== Write =====

    @Transactional
    public DepartmentResponse create(Long facilityId, CreateDepartmentRequest request) {
        if (departmentRepository.existsByNameAndFacilityId(request.name(), facilityId)) {
            throw new IllegalArgumentException(
                    "Department '" + request.name() + "' already exists in this facility");
        }
        Department department = new Department(
                request.name(),
                request.code().toUpperCase(Locale.ROOT),
                request.description(),
                java.math.BigDecimal.ZERO,
                // Legacy-plane hospital link: resolved from the tenant id
                // (a facility maps to a hospital in Phase 2; may be null once
                // self-serve onboarding provisions facilities without one).
                hospitalRepository.findById(facilityId).orElse(null)
        );
        department.setFacilityId(facilityId);
        department.setStatus("active");
        department.setHeadDoctorName(request.headDoctorName());
        department.setRooms(request.rooms());
        department.setOpensAt(request.opensAt());
        department.setClosesAt(request.closesAt());
        department.setTwentyFourSeven(Boolean.TRUE.equals(request.twentyFourSeven()));
        return toResponse(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse update(Long facilityId, Long id, UpdateDepartmentRequest request) {
        Department department = findOwned(facilityId, id);

        if (request.name() != null) department.setName(request.name());
        if (request.code() != null) department.setAbbreviation(request.code().toUpperCase(Locale.ROOT));
        if (request.description() != null) department.setDescription(request.description());
        if (request.headDoctorName() != null) department.setHeadDoctorName(request.headDoctorName());
        if (request.rooms() != null) department.setRooms(request.rooms());
        if (request.opensAt() != null) department.setOpensAt(request.opensAt());
        if (request.closesAt() != null) department.setClosesAt(request.closesAt());
        if (request.twentyFourSeven() != null) department.setTwentyFourSeven(request.twentyFourSeven());
        if (request.status() != null) {
            String status = request.status().toLowerCase(Locale.ROOT);
            if (!VALID_STATUSES.contains(status)) {
                throw new IllegalArgumentException(
                        "Invalid status '" + request.status() + "'. Must be one of: active, closed, archived");
            }
            department.setStatus(status);
        }
        // NOTE: totalDoctors is accepted in the DTO but derived server-side
        // from StaffMember rows — client values are intentionally ignored (§10.2).

        return toResponse(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse assignHeadDoctor(Long facilityId, Long id, String headDoctorName) {
        Department department = findOwned(facilityId, id);
        department.setHeadDoctorName(headDoctorName);
        return toResponse(departmentRepository.save(department));
    }

    @Transactional
    public void delete(Long facilityId, Long id) {
        Department department = findOwned(facilityId, id);
        DepartmentResponse current = toResponse(department);
        // canDelete gate (§7.4): no waiting, no in-consultation, no appointments today.
        if (current.waiting() != 0 || current.inConsultation() != 0 || current.appointmentsToday() != 0) {
            throw new ConflictException(
                    "Cannot delete department '" + department.getName()
                            + "' — it has live activity (waiting/in-consultation/appointments today)."
                            + " Archive it instead.");
        }
        // Staff assigned to the department block deletion with a clear message
        // (legacy Doctor rows are additionally protected by the DB foreign key).
        String deptId = String.valueOf(id);
        boolean hasStaff = staffMemberRepository.findByFacilityId(facilityId).stream()
                .anyMatch(s -> deptId.equals(s.getDepartmentId()));
        if (hasStaff) {
            throw new ConflictException(
                    "Cannot delete department '" + department.getName()
                            + "' — staff are still assigned to it. Reassign or deactivate them first.");
        }
        departmentRepository.delete(department);
        log.info("Facility {} deleted department {}", facilityId, id);
    }

    // ===== Helpers =====

    /** Normalized status — legacy rows have a NULL column; treat as active. */
    private static String statusOf(Department d) {
        return d.getStatus() != null ? d.getStatus() : "active";
    }

    private Department findOwned(Long facilityId, Long id) {
        return departmentRepository.findByIdAndFacilityId(id, facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in this facility"));
    }

    private DepartmentResponse toResponse(Department d) {
        Long facilityId = d.getFacilityId();
        String deptId = String.valueOf(d.getId());
        int totalDoctors = (int) staffMemberRepository
                .countByRoleAndDepartmentIdAndAccountStatusAndFacilityId(
                        StaffRole.DOCTOR, deptId, StaffAccountStatus.ACTIVE, facilityId);
        int doctorsOnDuty = (int) staffMemberRepository
                .countByRoleAndDepartmentIdAndAccountStatusAndDutyStatusAndFacilityId(
                        StaffRole.DOCTOR, deptId, StaffAccountStatus.ACTIVE,
                        StaffDutyStatus.ON_DUTY, facilityId);
        return DepartmentResponse.from(d, doctorsOnDuty, totalDoctors,
                0, 0, 0, countTodayBookings(d));
    }

    /** Bookings today for the department, excluding cancelled (§5.3 appointmentsToday). */
    private int countTodayBookings(Department d) {
        LocalDate today = LocalDate.now();
        return (int) bookingRepository
                .findByDepartmentIdAndBookingDateBetween(
                        d.getId(), today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .count();
    }
}
