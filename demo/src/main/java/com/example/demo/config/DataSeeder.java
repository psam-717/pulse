package com.example.demo.config;

import com.example.demo.model.Booking;
import com.example.demo.model.BookingStatus;
import com.example.demo.model.Department;
import com.example.demo.model.Doctor;
import com.example.demo.model.Gender;
import com.example.demo.model.Hospital;
import com.example.demo.model.HospitalAdmin;
import com.example.demo.model.AdminRole;
import com.example.demo.model.Patient;
import com.example.demo.model.PatientSource;
import com.example.demo.model.PaymentStatus;
import com.example.demo.model.QueueEntry;
import com.example.demo.model.QueuePriority;
import com.example.demo.model.QueueStatus;
import com.example.demo.model.StaffAccountStatus;
import com.example.demo.model.StaffDutyStatus;
import com.example.demo.model.StaffMember;
import com.example.demo.model.StaffRole;
import com.example.demo.model.TimeSlot;
import com.example.demo.model.VerificationStatus;
import com.example.demo.model.WorkingHours;
import com.example.demo.repository.BookingRepository;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.DoctorRepository;
import com.example.demo.repository.HospitalAdminRepository;
import com.example.demo.repository.HospitalRepository;
import com.example.demo.repository.PatientRepository;
import com.example.demo.repository.QueueEntryRepository;
import com.example.demo.repository.StaffMemberRepository;
import com.example.demo.repository.TimeSlotRepository;
import com.example.demo.repository.WorkingHoursRepository;
import com.example.demo.repository.OperationalSettingsRepository;
import com.example.demo.model.OperationalSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final HospitalRepository hospitalRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final HospitalAdminRepository adminRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final PatientRepository patientRepository;
    private final BookingRepository bookingRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final OperationalSettingsRepository operationalSettingsRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder;

    public DataSeeder(HospitalRepository hospitalRepository,
                      DepartmentRepository departmentRepository,
                      DoctorRepository doctorRepository,
                      HospitalAdminRepository adminRepository,
                      TimeSlotRepository timeSlotRepository,
                      StaffMemberRepository staffMemberRepository,
                      PatientRepository patientRepository,
                      BookingRepository bookingRepository,
                      QueueEntryRepository queueEntryRepository,
                      OperationalSettingsRepository operationalSettingsRepository,
                      WorkingHoursRepository workingHoursRepository,
                      org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.hospitalRepository = hospitalRepository;
        this.departmentRepository = departmentRepository;
        this.doctorRepository = doctorRepository;
        this.adminRepository = adminRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.patientRepository = patientRepository;
        this.bookingRepository = bookingRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.operationalSettingsRepository = operationalSettingsRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public void run(String... args) {
        ensureConstraintRepair();
        ensureSettingsColumns();
        // Only seed if no data exists yet
        if (hospitalRepository.count() > 0) {
            ensureSuperAdminExists();
            seedTimeSlotsIfEmpty();
            ensureFacilityDemoData();
            ensureDemoMedicalProfiles();
            ensureDiscoveryDemoData();
            ensureDemoInsurance();
            log.info("Database already seeded — skipping");
            return;
        }

        log.info("Seeding initial data...");

        // --- HOSPITALS ---
        Hospital korleBu = hospitalRepository.save(
                new Hospital("Korle Bu Teaching Hospital",
                        "MLSC-KB-2020-001",
                        "Guggisberg Ave, Accra", "+233 302 665 901",
                        "info@korlebu.gov.gh")
        );

        Hospital ridge = hospitalRepository.save(
                new Hospital("Ridge Hospital",
                        "MLSC-RH-2015-002",
                        "Castle Rd, Accra", "+233 302 220 000",
                        "info@ridgehospital.gh")
        );

        // --- DEPARTMENTS ---
        Department cardio = departmentRepository.save(
                new Department("Cardiology", "CARDIO",
                        "Heart and cardiovascular system diagnosis & treatment",
                        new java.math.BigDecimal("350.00"), korleBu)
        );

        Department ortho = departmentRepository.save(
                new Department("Orthopedics", "ORTHO",
                        "Musculoskeletal system, bones, joints & spine",
                        new java.math.BigDecimal("400.00"), korleBu)
        );

        Department peds = departmentRepository.save(
                new Department("Pediatrics", "PEDS",
                        "Medical care for infants, children & adolescents",
                        new java.math.BigDecimal("250.00"), ridge)
        );

        Department neuro = departmentRepository.save(
                new Department("Neurology", "NEURO",
                        "Nervous system disorders & brain health",
                        new java.math.BigDecimal("500.00"), ridge)
        );

        // --- DOCTORS (workspace IDs auto-generated by DoctorAdminService) ---
        String password = passwordEncoder.encode("admin123");

        // Korle Bu - Cardiology
        doctorRepository.save(new Doctor("Yaw", "Appiah", "Interventional Cardiology",
                "yaw.appiah@korlebu.gov.gh", "+233 501 111 001", "GC-2024-001",
                "CARDIO-DOC-001", password, korleBu, cardio));

        doctorRepository.save(new Doctor("Akua", "Mensah", "Cardiac Electrophysiology",
                "akua.mensah@korlebu.gov.gh", "+233 501 111 002", "GC-2024-002",
                "CARDIO-DOC-002", password, korleBu, cardio));

        // Korle Bu - Orthopedics
        doctorRepository.save(new Doctor("Kwame", "Ofori", "Sports Orthopedics",
                "kwame.ofori@korlebu.gov.gh", "+233 501 111 003", "GC-2024-003",
                "ORTHO-DOC-001", password, korleBu, ortho));

        // Ridge - Pediatrics
        doctorRepository.save(new Doctor("Esi", "Quartey", "General Pediatrics",
                "esi.quartey@ridgehospital.gh", "+233 502 222 001", "GC-2024-004",
                "PEDS-DOC-001", password, ridge, peds));

        // Ridge - Neurology
        doctorRepository.save(new Doctor("Nana", "Boateng", "Neurology",
                "nana.boateng@ridgehospital.gh", "+233 502 222 002", "GC-2024-005",
                "NEURO-DOC-001", password, ridge, neuro));

        log.info("✅ Seeded: 2 hospitals, 4 departments, 5 doctors");

        // --- TIME SLOTS (3 days, 8AM–5PM, 20-min intervals) ---
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalTime startTime = java.time.LocalTime.of(8, 0);
        java.time.LocalTime endTime = java.time.LocalTime.of(17, 0);
        int slotDuration = 20; // minutes (matches default consultationDuration)
        int slotsGenerated = 0;

        for (Doctor doc : doctorRepository.findAll()) {
            for (int dayOffset = 0; dayOffset < 3; dayOffset++) {
                java.time.LocalDate slotDate = today.plusDays(dayOffset);
                for (java.time.LocalTime t = startTime; t.isBefore(endTime); t = t.plusMinutes(slotDuration)) {
                    timeSlotRepository.save(new TimeSlot(doc, slotDate, t, t.plusMinutes(slotDuration)));
                    slotsGenerated++;
                }
            }
        }
        log.info("✅ Seeded: {} time slots across {} doctors for 3 days", slotsGenerated, doctorRepository.count());

        // --- SUPER ADMIN (not tied to any hospital) ---
        String superPassword = passwordEncoder.encode("superadmin123");
        adminRepository.save(new HospitalAdmin(
                null, "Super Admin", "superadmin@pulse.gh",
                superPassword, "+233 500 000 000", AdminRole.SUPER_ADMIN
        ));
        log.info("✅ Super admin created: superadmin@pulse.gh / superadmin123");
        log.info("🔑 Doctor password: admin123");

        // Facility-plane demo accounts + departments (web dashboard login)
        ensureFacilityDemoData();
        ensureDemoMedicalProfiles();
        ensureDiscoveryDemoData();
        ensureDemoInsurance();
    }

    /**
     * Drops stale CHECK constraints on enums. PostgreSQL CHECK constraints
     * are NOT updated by Hibernate's ddl-auto=update — the ones created when
     * PaymentStatus/BookingStatus had fewer values still block new enum
     * values (e.g. REFUNDED) with a runtime constraint violation. Enum
     * validation belongs to the Java layer, so the constraints are dropped.
     */
    private void ensureConstraintRepair() {
        try {
            jdbcTemplate.execute("ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_payment_status_check");
            jdbcTemplate.execute("ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check");
            // Covers PaymentStatus.REFUNDED and BookingStatus.PENDING_PAYMENT (P3).
            log.info("✅ Repaired stale booking CHECK constraints (bookings_payment_status_check / bookings_status_check)");
        } catch (Exception e) {
            log.warn("Booking constraint repair skipped: {}", e.getMessage());
        }
    }

    private void ensureSuperAdminExists() {
        if (adminRepository.findByEmail("superadmin@pulse.gh").isEmpty()) {
            String superPassword = passwordEncoder.encode("superadmin123");
            adminRepository.save(new HospitalAdmin(
                    null, "Super Admin", "superadmin@pulse.gh",
                    superPassword, "+233 500 000 000", AdminRole.SUPER_ADMIN
            ));
            log.info("✅ Super admin created (lazy): superadmin@pulse.gh / superadmin123");
        }

        // Reset all hospital admin passwords to "admin123" every restart (dev only)
        String devPassword = passwordEncoder.encode("admin123");
        for (HospitalAdmin admin : adminRepository.findAll()) {
            if (admin.getRole() != AdminRole.SUPER_ADMIN && !passwordEncoder.matches("admin123", admin.getPassword())) {
                admin.setPassword(devPassword);
                adminRepository.save(admin);
                log.info("🔄 Reset password for: {}", admin.getEmail());
            }
        }
    }

    private void seedTimeSlotsIfEmpty() {
        if (timeSlotRepository.count() > 0) {
            return; // already seeded
        }
        log.info("⏰ Seeding time slots for existing doctors...");
        java.time.LocalDate today = java.time.LocalDate.now();
        int slotsGenerated = 0;
        for (Doctor doc : doctorRepository.findAll()) {
            for (int dayOffset = 0; dayOffset < 3; dayOffset++) {
                java.time.LocalDate slotDate = today.plusDays(dayOffset);
                for (java.time.LocalTime t = java.time.LocalTime.of(8, 0);
                        t.isBefore(java.time.LocalTime.of(17, 0));
                        t = t.plusMinutes(20)) {
                    timeSlotRepository.save(new TimeSlot(doc, slotDate, t, t.plusMinutes(20)));
                    slotsGenerated++;
                }
            }
        }
        log.info("✅ Seeded {} time slots", slotsGenerated);
    }

    /**
     * Facility-plane demo data (web dashboard). Idempotent and self-healing:
     * 1. Backfills department.facilityId from the legacy hospital link.
     * 2. Ensures the six demo departments exist for the first facility.
     * 3. Repairs staff departmentId values seeded pre-Phase-2 (name strings
     *    → real numeric ids).
     * 4. Ensures a demo staff roster (matches the frontend mock's demo
     *    identities; password Password123! for all, dev only).
     */
    private void ensureFacilityDemoData() {
        Hospital facility = hospitalRepository.findAll().stream().findFirst().orElse(null);
        if (facility == null) {
            return;
        }
        Long facilityId = facility.getId();

        // 1. Backfill department facilityIds from the legacy hospital link
        for (Department d : departmentRepository.findAll()) {
            if (d.getFacilityId() == null && d.getHospital() != null) {
                d.setFacilityId(d.getHospital().getId());
                departmentRepository.save(d);
            }
        }

        // 2. Demo departments (by name, by abbreviation)
        String[][] demoDepts = {
                {"Cardiology", "CARD", "Heart and cardiovascular care", "08:00", "17:00", "false", "3"},
                {"Emergency", "EMG", "Acute and emergency care", "00:00", "23:59", "true", "5"},
                {"General Medicine", "GEN", "Outpatient general consultation", "08:00", "17:00", "false", "4"},
                {"Maternity", "MAT", "Antenatal, delivery and postnatal care", "00:00", "23:59", "true", "3"},
                {"Pediatrics", "PEDS", "Child and infant health", "08:00", "16:00", "false", "2"},
                {"Laboratory", "LAB", "Diagnostics and sample processing", "08:00", "15:00", "false", "2"},
        };
        for (String[] d : demoDepts) {
            if (!departmentRepository.existsByNameAndFacilityId(d[0], facilityId)
                    && departmentRepository.findByAbbreviation(d[1]).isEmpty()) {
                Department dept = new Department(
                        d[0], d[1], d[2], new java.math.BigDecimal("300.00"), facility);
                dept.setFacilityId(facilityId);
                dept.setStatus("active");
                dept.setRooms(Integer.parseInt(d[6]));
                dept.setOpensAt(d[3]);
                dept.setClosesAt(d[4]);
                dept.setTwentyFourSeven(Boolean.parseBoolean(d[5]));
                departmentRepository.save(dept);
            }
        }

        // 3. Repair staff departmentId values seeded pre-Phase-2
        for (StaffMember s : staffMemberRepository.findAll()) {
            if (s.getDepartmentId() == null || s.getDepartmentId().isEmpty()) continue;
            try {
                Long.parseLong(s.getDepartmentId());
                continue; // already numeric
            } catch (NumberFormatException ignored) {
                // fall through — name string from the Phase-1 seed
            }
            departmentRepository.findByFacilityId(facilityId).stream()
                    .filter(d -> d.getName().equalsIgnoreCase(s.getDepartmentId())
                            || d.getName().equalsIgnoreCase(s.getDepartmentName()))
                    .findFirst()
                    .ifPresent(d -> {
                        s.setDepartmentId(String.valueOf(d.getId()));
                        s.setDepartmentName(d.getName());
                        staffMemberRepository.save(s);
                    });
        }

        // 4. Demo staff roster (by email; password Password123! for all)
        String staffPassword = passwordEncoder.encode("Password123!");
        String[][] demoStaff = {
                // name, role, title, specialty, departmentName
                {"Sarah Jenkins", "ADMIN", "Chief Administrator", "", "General Medicine"},
                {"Dr. Owusu", "DOCTOR", "Cardiologist", "Interventional Cardiology", "Cardiology"},
                {"Dr. Kusi", "DOCTOR", "Cardiologist", "Electrophysiology", "Cardiology"},
                {"Nurse Affum", "NURSE", "Senior Nurse", "", "Cardiology"},
                {"Dr. Boateng", "DOCTOR", "General Practitioner", "Internal Medicine", "General Medicine"},
                {"Adwoa Boateng", "FRONT_DESK", "Front Desk", "", "General Medicine"},
                {"Dr. Mensima", "DOCTOR", "Emergency Physician", "Trauma Medicine", "Emergency"},
                {"Nurse Acheampong", "NURSE", "Charge Nurse", "", "Emergency"},
        };
        String[] demoEmails = {
                "sarah.jenkins@knust-hospital.test",
                "owusu@pulsehealth.test",
                "kusi@pulsehealth.test",
                "affum@pulsehealth.test",
                "boateng@pulsehealth.test",
                "adwoa@pulsehealth.test",
                "mensima@pulsehealth.test",
                "acheampong@pulsehealth.test",
        };
        for (int i = 0; i < demoStaff.length; i++) {
            String email = demoEmails[i];
            if (staffMemberRepository.existsByEmail(email)) continue;
            String[] s = demoStaff[i];
            String deptName = s[4];
            String deptId = departmentRepository.findByFacilityId(facilityId).stream()
                    .filter(d -> d.getName().equalsIgnoreCase(deptName))
                    .map(d -> String.valueOf(d.getId()))
                    .findFirst()
                    .orElse("");
            staffMemberRepository.save(new StaffMember(
                    s[0], StaffRole.valueOf(s[1]), s[2],
                    s[3].isEmpty() ? null : s[3],
                    deptId, deptName,
                    email, "+233 500 111 001",
                    "08:00", "17:00",
                    StaffDutyStatus.ON_DUTY, StaffAccountStatus.ACTIVE,
                    null, facilityId, staffPassword));
        }
        log.info("✅ Facility demo data ensured for facility {} ({} departments, {} staff)",
                facilityId, departmentRepository.findByFacilityId(facilityId).size(),
                staffMemberRepository.findByFacilityId(facilityId).size());

        ensureFacilitySettings(facility);

        ensureDemoBookings();
    }

    /**
     * Mobile medical profile demo data (ARCHITECTURE.md §8 P1) — mirrors the
     * pulse-mobile store seeds (medical-store, insurance-store). Idempotent:
     * only fills patients whose structured fields are still null, so it runs
     * on both fresh and already-seeded databases.
     */
    private void ensureDemoMedicalProfiles() {
        // [phone, bloodGroup, allergiesJson, conditionsJson, medicationsJson,
        //  vitalsJson, ecName, ecRelationship, ecPhone]
        String[][] profiles = {
                {"+233 24 111 0001", "O+",
                        "[{\"id\":\"al-0001\",\"label\":\"Penicillin\",\"type\":\"drug\"}]",
                        "[{\"id\":\"co-0001\",\"label\":\"Asthma\"}]",
                        "[{\"id\":\"me-0001\",\"name\":\"Ventolin Inhaler\",\"dose\":\"100mcg, as needed\"}]",
                        "[{\"id\":\"vt-0001\",\"date\":\"2026-08-18\",\"systolic\":\"120\",\"diastolic\":\"80\",\"pulseBpm\":\"72\",\"temperatureC\":\"36.8\",\"heightCm\":\"178\",\"weightKg\":\"74\"}]",
                        "Ama Quarcoo", "Sister", "+233 20 987 6543"},
                {"+233 24 111 0002", "A+",
                        "[]", "[]",
                        "[{\"id\":\"me-0002\",\"name\":\"Lisinopril\",\"dose\":\"10mg, once daily\"}]",
                        "[{\"id\":\"vt-0002\",\"date\":\"2026-08-12\",\"systolic\":\"118\",\"diastolic\":\"76\",\"pulseBpm\":\"68\",\"temperatureC\":\"36.6\",\"heightCm\":\"165\",\"weightKg\":\"61\"}]",
                        "Yaw Asante", "Brother", "+233 20 111 2233"},
                {"+233 24 111 0003", "B+",
                        "[{\"id\":\"al-0002\",\"label\":\"Aspirin\",\"type\":\"drug\"}]",
                        "[{\"id\":\"co-0002\",\"label\":\"Hypertension\"}]",
                        "[{\"id\":\"me-0003\",\"name\":\"Metformin\",\"dose\":\"500mg, twice daily\"}]",
                        "[]",
                        "Adjoa Ofori", "Wife", "+233 24 555 7788"},
        };

        for (String[] row : profiles) {
            patientRepository.findByPhone(row[0]).ifPresent(p -> {
                if (p.getAllergiesJson() == null) {
                    p.setAllergiesJson(row[2]);
                    p.setConditionsJson(row[3]);
                    p.setMedicationsJson(row[4]);
                    p.setVitalsJson(row[5]);
                    p.setEmergencyContactName(row[6]);
                    p.setEmergencyContactRelationship(row[7]);
                    p.setEmergencyContactPhone(row[8]);
                    patientRepository.save(p);
                }
            });
        }
        log.info("✅ Demo medical profiles ensured (P1 mobile)");
    }

    /**
     * Mobile insurance demo data (ARCHITECTURE.md §8 P2) — mirrors the
     * pulse-mobile insurance-store seed. Idempotent: only fills patients
     * whose insurance_scheme is still null, so a PUT from the app survives
     * a reboot (PITFALLS.md §2.2).
     */
    private void ensureDemoInsurance() {
        // [phone, scheme, membershipNumber, cardholderName, expiryDate]
        String[][] rows = {
                {"+233 24 111 0001", "NHIS", "NHIS-00101-2026", "Kwame Mensah", "2027-01-31"},
                {"+233 24 111 0002", "NHIS", "NHIS-00102-2026", "Abena Asante", "2026-12-31"},
        };
        for (String[] row : rows) {
            patientRepository.findByPhone(row[0]).ifPresent(p -> {
                if (p.getInsuranceScheme() == null && p.getInsuranceMembershipNumber() == null) {
                    p.setInsuranceScheme(row[1]);
                    p.setInsuranceMembershipNumber(row[2]);
                    p.setInsuranceCardholderName(row[3]);
                    p.setInsuranceExpiryDate(java.time.LocalDate.parse(row[4]));
                    patientRepository.save(p);
                }
            });
        }
        log.info("✅ Demo insurance records ensured (P2 mobile)");
    }

    /**
     * Discovery-plane demo data (ARCHITECTURE.md §8 P2 / G4):
     * approve the seeded teaching hospitals, backfill lat/lng/specialties
     * only when missing, add KNUST University Hospital if absent, and seed
     * default working hours (Mon–Sat 08:00–17:00, Sunday closed) when a
     * hospital has none. Never overwrites user-edited hospital fields.
     */
    private void ensureDiscoveryDemoData() {
        for (Hospital h : hospitalRepository.findAll()) {
            if (h.getVerificationStatus() == VerificationStatus.PENDING
                    && h.getRejectionReason() == null
                    && isSeededDiscoveryHospital(h)) {
                h.setVerificationStatus(VerificationStatus.APPROVED);
            }
            if (h.getLatitude() == null || h.getLongitude() == null) {
                double[] coords = defaultCoordsFor(h.getName());
                if (coords != null) {
                    h.setLatitude(coords[0]);
                    h.setLongitude(coords[1]);
                }
            }
            if (h.getSpecialties() == null || h.getSpecialties().isBlank()) {
                String specs = defaultSpecialtiesFor(h.getName());
                if (specs != null) h.setSpecialties(specs);
            }
            hospitalRepository.save(h);
            ensureDefaultWorkingHours(h);
        }
        ensureKnustHospital();
        ensureKnustDoctor();
        log.info("✅ Discovery demo data ensured (approved hospitals + working hours)");
    }

    private static boolean isSeededDiscoveryHospital(Hospital h) {
        String license = h.getLicenseNumber() == null ? "" : h.getLicenseNumber();
        String name = h.getName() == null ? "" : h.getName();
        return license.startsWith("MLSC-")
                || name.contains("Korle Bu")
                || name.contains("Ridge")
                || name.contains("KNUST");
    }

    private static double[] defaultCoordsFor(String name) {
        if (name == null) return null;
        if (name.contains("Korle Bu")) return new double[]{5.5379, -0.2260};
        if (name.contains("Ridge")) return new double[]{5.5603, -0.1969};
        if (name.contains("KNUST")) return new double[]{6.6745, -1.5716};
        return null;
    }

    private static String defaultSpecialtiesFor(String name) {
        if (name == null) return null;
        if (name.contains("Korle Bu")) return "[\"Cardiology\",\"Orthopedics\"]";
        if (name.contains("Ridge")) return "[\"Pediatrics\",\"Neurology\"]";
        if (name.contains("KNUST")) return "[\"Cardiology\",\"Pediatrics\",\"General OPD\"]";
        return null;
    }

    private void ensureDefaultWorkingHours(Hospital hospital) {
        if (!workingHoursRepository.findByHospitalId(hospital.getId()).isEmpty()) return;
        java.time.LocalTime open = java.time.LocalTime.of(8, 0);
        java.time.LocalTime close = java.time.LocalTime.of(17, 0);
        for (int dow = 1; dow <= 7; dow++) {
            WorkingHours wh = new WorkingHours(hospital, dow, open, close);
            wh.setClosed(dow == 7);
            workingHoursRepository.save(wh);
        }
    }

    private void ensureKnustHospital() {
        boolean exists = hospitalRepository.findAll().stream()
                .anyMatch(h -> h.getName() != null && h.getName().contains("KNUST"));
        if (exists) return;

        Hospital knust = new Hospital(
                "KNUST University Hospital",
                "MLSC-KNUST-2018-003",
                "University Road, Kumasi",
                "+233 322 060 311",
                "info@uhs.knust.edu.gh");
        knust.setVerificationStatus(VerificationStatus.APPROVED);
        knust.setLatitude(6.6745);
        knust.setLongitude(-1.5716);
        knust.setRegion("Ashanti");
        knust.setFacilityType("hospital");
        knust.setSpecialties("[\"Cardiology\",\"Pediatrics\",\"General OPD\"]");
        knust = hospitalRepository.save(knust);

        if (departmentRepository.findByAbbreviation("K-GOPD").isEmpty()) {
            Department gopd = new Department(
                    "General OPD", "K-GOPD",
                    "Walk-in outpatient consultation for general cases",
                    new java.math.BigDecimal("20.00"), knust);
            gopd.setFacilityId(knust.getId());
            gopd.setStatus("active");
            gopd.setOpensAt("08:00");
            gopd.setClosesAt("17:00");
            gopd.setTwentyFourSeven(false);
            departmentRepository.save(gopd);
        }
        ensureDefaultWorkingHours(knust);
        log.info("✅ KNUST University Hospital seeded for mobile discovery");
    }

    /**
     * KNUST General OPD was seeded without a doctor in P2. P3 bookings
     * require a TimeSlot.doctor — add one if the department is empty.
     */
    private void ensureKnustDoctor() {
        Department gopd = departmentRepository.findByAbbreviation("K-GOPD").orElse(null);
        if (gopd == null) return;
        if (doctorRepository.countByDepartmentId(gopd.getId()) > 0) return;
        Hospital knust = gopd.getHospital();
        if (knust == null && gopd.getFacilityId() != null) {
            knust = hospitalRepository.findById(gopd.getFacilityId()).orElse(null);
        }
        if (knust == null) return;
        Doctor doc = new Doctor("Ama", "Boateng", "General Practice",
                "ama.boateng@uhs.knust.edu.gh", "+233 322 060 312", "GC-2026-017",
                "KNUST-GOPD-DOC-001", passwordEncoder.encode("admin123"),
                knust, gopd);
        doctorRepository.save(doc);
        log.info("✅ KNUST General OPD doctor seeded for mobile booking");
    }

    /**
     * Hibernate ddl-auto=update does not reliably ALTER existing tables for
     * new columns (it added the hospitals columns but skipped staff_members
     * in the Phase 6 bring-up). Run the same idempotent ADD COLUMN IF NOT
     * EXISTS repair the constraint repair uses, BEFORE any entity query.
     */
    private void ensureSettingsColumns() {
        jdbcTemplate.execute("ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS email_on_new_appointment boolean DEFAULT true");
        jdbcTemplate.execute("ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS email_on_no_show boolean DEFAULT true");
        jdbcTemplate.execute("ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS sms_on_queue_alert boolean DEFAULT false");
        jdbcTemplate.execute("ALTER TABLE staff_members ADD COLUMN IF NOT EXISTS daily_summary_email boolean DEFAULT true");
        jdbcTemplate.execute("ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS region varchar(255)");
        jdbcTemplate.execute("ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS facility_type varchar(255) DEFAULT 'hospital'");
        jdbcTemplate.execute("ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS logo_url varchar(255)");
        // Mobile self-service medical profile (P1): structured JSON fields +
        // emergency contact, kept in sync with the legacy flat columns.
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS allergies_json TEXT");
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS conditions_json TEXT");
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS medications_json TEXT");
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS vitals_json TEXT");
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS emergency_contact_name varchar(255)");
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS emergency_contact_relationship varchar(255)");
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS emergency_contact_phone varchar(255)");
        // Mobile insurance (P2): patient-scoped NHIS/private record + card photo URL.
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS insurance_scheme varchar(255)");
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS insurance_membership_number varchar(255)");
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS insurance_cardholder_name varchar(255)");
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS insurance_expiry_date date");
        jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS insurance_card_photo_url varchar(512)");
        // P3: pay-by deadline on bookings + operational window (default 48h).
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS pay_by_deadline timestamp");
        jdbcTemplate.execute("ALTER TABLE operational_settings ADD COLUMN IF NOT EXISTS pay_by_deadline_hours integer DEFAULT 48");
        // Use NOW()+48h, not booking_date+48h: booking_date is the create
        // instant, so old unpaid demo rows would expire on the first job tick.
        jdbcTemplate.update("""
                UPDATE bookings
                   SET pay_by_deadline = NOW() + INTERVAL '48 hours'
                 WHERE pay_by_deadline IS NULL
                """);
        log.info("✅ Settings columns ensured (staff_members prefs, hospitals settings-plane, patients mobile medical + insurance, bookings pay-by)");
    }

    /**
     * Settings-plane demo defaults (Phase 6): the facility row's profile
     * fields (region/facilityType — real per-facility values) and the
     * operational settings row (frontend-mock defaults, queue priority
     * levels emergency/urgent/routine 3/2/1). Idempotent.
     */
    private void ensureFacilitySettings(Hospital facility) {
        if (facility.getRegion() == null || facility.getRegion().isBlank()) {
            facility.setRegion("Greater Accra");
            facility.setFacilityType("hospital");
            hospitalRepository.save(facility);
        }
        if (operationalSettingsRepository.findByFacilityId(facility.getId()).isEmpty()) {
            OperationalSettings settings = new OperationalSettings(facility.getId());
            settings.setQueuePriorityLevelsJson(
                    "emergency:Emergency:3|urgent:Urgent:2|routine:Routine:1");
            operationalSettingsRepository.save(settings);
            log.info("✅ Operational settings seeded for facility {}", facility.getId());
        }
    }

    /**
     * Demo patients + today's bookings so the web Appointments page has real
     * data (the mobile booking IS the facility appointment — D4). Idempotent
     * by phone + patient/slot pair.
     */
    private void ensureDemoBookings() {
        Hospital facility = hospitalRepository.findAll().stream().findFirst().orElse(null);
        if (facility == null) return;
        Long facilityId = facility.getId();

        Patient ama = ensurePatient("Ama", "Serwaa", Gender.FEMALE, "+233 24 000 0001", "ama.serwaa@pulsehealth.test");
        Patient kofi = ensurePatient("Kofi", "Asante", Gender.MALE, "+233 24 000 0002", "kofi.asante@pulsehealth.test");
        Patient efua = ensurePatient("Efua", "Gyasi", Gender.FEMALE, "+233 24 000 0003", "efua.gyasi@pulsehealth.test");

        // The facility-plane doctor (staff "Dr. Owusu") also needs a legacy
        // Doctor row — the web workspace filters appointments by
        // doctorName === session.name, and bookings attach to legacy doctors.
        ensureLegacyDoctorOwusu(facility);

        java.time.LocalDate today = java.time.LocalDate.now();
        // phone, startTime, bookingStatus, paymentStatus, doctorEmail
        String[][] plan = {
                {"+233 24 000 0001", "09:00", "CONFIRMED", "PAID", "owusu@pulsehealth.test"},
                {"+233 24 000 0002", "10:00", "CONFIRMED", "PAID", "owusu@pulsehealth.test"},
                {"+233 24 000 0003", "11:00", "PENDING_PAYMENT", "PENDING", "akua.mensah@korlebu.gov.gh"},
                {"+233 24 000 0001", "12:00", "CANCELLED", "PENDING", "owusu@pulsehealth.test"},
        };
        for (String[] row : plan) {
            String[] r = row;
            Patient patient = r[0].equals(ama.getPhone()) ? ama
                    : r[0].equals(kofi.getPhone()) ? kofi : efua;
            Doctor doctor = doctorRepository.findAll().stream()
                    .filter(d -> r[4].equals(d.getEmail()))
                    .findFirst()
                    .orElse(null);
            if (doctor == null) continue;
            TimeSlot slot = ensureSlot(doctor, today, java.time.LocalTime.parse(r[1]));
            if (slot == null) continue;
            if (bookingRepository.existsByPatientIdAndTimeSlotId(patient.getId(), slot.getId())) continue;

            // Resolve the department through the repository — the entity's
            // lazy proxy can't be initialized outside a transaction.
            Long deptId = doctor.getDepartment() != null ? doctor.getDepartment().getId() : null;
            Department dept = deptId != null
                    ? departmentRepository.findById(deptId).orElse(null) : null;
            if (dept == null) continue;

            Booking booking = new Booking(patient, doctor, dept, facility,
                    slot, dept.getConsultationFee());
            booking.setStatus(BookingStatus.valueOf(r[2]));
            booking.setPaymentStatus(PaymentStatus.valueOf(r[3]));
            bookingRepository.save(booking);
        }
        log.info("✅ Demo bookings ensured for today ({} total)", bookingRepository.count());

        ensureDemoPatientsAndQueue();
    }

    /**
     * Demo patient directory + live queue, mirroring the frontend mocks
     * (lib/mock/patients.ts, lib/mock/queue.ts) so the doctor workspace and
     * dashboard render real-looking data. Idempotent by phone/ticket.
     */
    private void ensureDemoPatientsAndQueue() {
        Hospital facility = hospitalRepository.findAll().stream().findFirst().orElse(null);
        if (facility == null) return;

        // [phone, first, last, gender, dob, patientNumber, bloodType, allergies, medsJson, vitalsJson]
        String[][] patients = {
                {"+233 24 111 0001", "Kwame", "Mensah", "MALE", "1985-03-14", "PT-00101", "O+", "penicillin",
                        "[{\"name\":\"Lisinopril\",\"dose\":\"10mg\",\"frequency\":\"Once daily\"}]",
                        "{\"bloodPressure\":\"128/82\",\"temperature\":\"36.8°C\",\"pulse\":\"74 bpm\",\"weight\":\"82 kg\",\"recordedAt\":\"2026-08-05T08:40\"}"},
                {"+233 24 111 0002", "Abena", "Asante", "FEMALE", "1992-07-22", "PT-00102", "A+", "",
                        "[]", null},
                {"+233 24 111 0003", "Kwabena", "Ofori", "MALE", "1978-11-02", "PT-00103", "B+", "aspirin",
                        "[]", null},
                {"+233 24 111 0004", "Yaw", "Darko", "MALE", "1969-01-30", "PT-00104", "O-", "",
                        "[{\"name\":\"Metformin\",\"dose\":\"500mg\",\"frequency\":\"Twice daily\"}]",
                        "{\"bloodPressure\":\"142/90\",\"temperature\":\"37.1°C\",\"pulse\":\"80 bpm\",\"weight\":\"88 kg\",\"recordedAt\":\"2026-08-05T08:55\"}"},
                {"+233 24 111 0005", "Ama", "Owusu", "FEMALE", "1995-09-18", "PT-00105", "AB+", "sulfa drugs",
                        "[]", null},
                {"+233 24 111 0006", "Kofi", "Antwi", "MALE", "1988-05-09", "PT-00106", "A-", "",
                        "[]", null},
                {"+233 24 111 0007", "Adwoa", "Sarpong", "FEMALE", "2001-12-25", "PT-00107", "B-", "",
                        "[]", null},
                {"+233 24 111 0008", "Esi", "Mensah", "FEMALE", "1982-04-11", "PT-00108", "O+", "penicillin",
                        "[]", "{\"bloodPressure\":\"118/76\",\"temperature\":\"36.6°C\",\"pulse\":\"68 bpm\",\"weight\":\"61 kg\",\"recordedAt\":\"2026-08-05T09:05\"}"},
                {"+233 24 111 0009", "Kojo", "Asare", "MALE", "1974-08-03", "PT-00109", "A+", "",
                        "[{\"name\":\"Atorvastatin\",\"dose\":\"20mg\",\"frequency\":\"Once nightly\"}]", null},
                {"+233 24 111 0010", "Akua", "Frimpong", "FEMALE", "1998-06-27", "PT-00110", "O+", "",
                        "[]", null},
        };

        for (String[] row : patients) {
            String phone = row[0];
            Patient p = patientRepository.findByPhone(phone).orElse(null);
            if (p == null) {
                p = new Patient(row[1], row[2],
                        java.time.LocalDate.parse(row[4]),
                        Gender.valueOf(row[3]),
                        row[1].toLowerCase() + "." + row[2].toLowerCase() + "@pulsehealth.test",
                        phone, "Kumasi, Ghana",
                        "GHA-00000000" + (phone.endsWith("10") ? "0" : phone.substring(phone.length() - 1)),
                        passwordEncoder.encode("patient123"));
                p.setPatientNumber(row[5]);
                p.setBloodType(row[6]);
                p.setAllergies(row[7].isEmpty() ? null : row[7]);
                p.setCurrentMedications(row[8]);
                p.setLatestVitals(row[9]);
            }
            // Existing patients: NEVER re-apply demo clinical values — the
            // mobile app edits these (P1 medical profile) and this seeder
            // used to clobber them on every boot. Only backfill the number.
            if (p.getPatientNumber() == null) {
                p.setPatientNumber(row[5]);
            }
            patientRepository.save(p);
        }

        // [ticket, patientName, deptId, status, priority, source, minutesAgo, calledMinAgo, clinician, room]
        String[][] queue = {
                {"C-001", "Kwame Mensah", "1", "IN_CONSULTATION", "ROUTINE", "APPOINTMENT", "28", "9", "Dr. Owusu", "Room 2"},
                {"C-002", "Abena Asante", "1", "WAITING", "URGENT", "WALK_IN", "22", null, null, null},
                {"C-003", "Yaw Darko", "1", "WAITING", "ROUTINE", "APPOINTMENT", "15", null, null, null},
                {"C-004", "Esi Boateng", "1", "WAITING", "ROUTINE", "WALK_IN", "6", null, null, null},
                {"E-001", "Nana Acheampong", "12", "IN_CONSULTATION", "EMERGENCY", "WALK_IN", "18", "12", "Dr. Mensima", "Bay 1"},
                {"E-002", "Adwoa Sarpong", "12", "WAITING", "EMERGENCY", "WALK_IN", "44", null, null, null},
                {"E-003", "Kwabena Osei", "12", "WAITING", "URGENT", "WALK_IN", "20", null, null, null},
                {"G-001", "Efua Tetteh", "13", "IN_CONSULTATION", "ROUTINE", "APPOINTMENT", "16", "5", "Dr. Boateng", "Room 8"},
                {"G-002", "Yaa Agyeman", "13", "WAITING", "ROUTINE", "APPOINTMENT", "9", null, null, null},
                {"G-003", "Kwesi Appiah", "13", "WAITING", "ROUTINE", "WALK_IN", "3", null, null, null},
        };
        for (String[] row : queue) {
            if (queueEntryRepository.findByTicketNumber(row[0]).isPresent()) continue;
            QueueEntry e = new QueueEntry(row[0], row[1], row[2],
                    QueuePriority.valueOf(row[4]), PatientSource.valueOf(row[5]),
                    LocalDateTime.now().minusMinutes(Long.parseLong(row[6])));
            e.setStatus(QueueStatus.valueOf(row[3]));
            if (row[7] != null && !row[7].isBlank()) {
                e.setCalledAt(LocalDateTime.now().minusMinutes(Long.parseLong(row[7])));
            }
            e.setClinician(row[8]);
            e.setRoom(row[9]);
            queueEntryRepository.save(e);
        }
        log.info("✅ Demo patients ({}) + queue entries ({}) ensured",
                patientRepository.count(), queueEntryRepository.count());
    }

    /**
     * Legacy Doctor row for the facility-plane staff "Dr. Owusu" — the web
     * workspace filters appointments by doctorName === session.name, and
     * bookings attach to legacy doctors, so the same person needs both rows.
     * Idempotent by email.
     */
    private Doctor ensureLegacyDoctorOwusu(Hospital facility) {
        return doctorRepository.findAll().stream()
                .filter(d -> "owusu@pulsehealth.test".equals(d.getEmail()))
                .findFirst()
                .orElseGet(() -> {
                    Department cardio = departmentRepository.findByFacilityId(facility.getId())
                            .stream().filter(d -> "Cardiology".equals(d.getName()))
                            .findFirst().orElse(null);
                    Doctor doc = new Doctor("Dr.", "Owusu", "Interventional Cardiology",
                            "owusu@pulsehealth.test", "+233 501 111 099", "GC-2024-099",
                            "CARDIO-DOC-099", passwordEncoder.encode("admin123"),
                            facility, cardio);
                    return doctorRepository.save(doc);
                });
    }

    private Patient ensurePatient(String firstName, String lastName, Gender gender,
                                  String phone, String email) {
        return patientRepository.findByPhone(phone).orElseGet(() -> {
            Patient p = new Patient(firstName, lastName,
                    java.time.LocalDate.of(1990, 1, 1), gender, email, phone,
                    "Kumasi", "GHA-000000000-" + (phone.endsWith("1") ? "1" : phone.endsWith("2") ? "2" : "3"),
                    passwordEncoder.encode("patient123"));
            return patientRepository.save(p);
        });
    }

    /** Returns an existing or freshly created slot for the doctor at the given time today. */
    private TimeSlot ensureSlot(Doctor doctor, java.time.LocalDate date, java.time.LocalTime start) {
        for (TimeSlot slot : timeSlotRepository.findByDoctorIdAndDateAndIsBooked(doctor.getId(), date, false)) {
            if (start.equals(slot.getStartTime())) {
                return slot;
            }
        }
        return timeSlotRepository.save(new TimeSlot(doctor, date, start, start.plusMinutes(20)));
    }
}