# Pulse v2 — Healthcare Matching Platform Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Evolve Pulse from a doctor-booking API into a full healthcare matching platform where hospitals create workspaces, patients discover and match with hospitals by specialty/location, and book appointments with real-time queue tracking.

**Architecture:** Spring Boot 4.0.4 + Java 25 + PostgreSQL backend. The platform connects three user types: **Patients** (mobile app), **Hospital Admins** (web dashboard), and **Doctors** (web/app). A hospital admin registers their hospital workspace → configures departments/schedules → adds doctors → patients discover and book → queue system manages flow.

**Tech Stack:** Spring Boot 4.0.4, Java 25, PostgreSQL, JPA/Hibernate, Spring Security + JWT, BCrypt, Jakarta Validation (`@Valid`), OpenStreetMap/Nominatim (geocoding), Redis (optional — for real-time queue).

---

## Current State (what exists)

| Component | Status |
|---|---|
| JWT auth filter/chain | ✅ Built |
| Patient signup (OTP) + login | ✅ Built |
| Doctor login (workspace ID) | ✅ Built |
| Discovery (hospitals → departments → doctors → slots) | ✅ Built |
| Booking CRUD | ✅ Built |
| Data seeder | ✅ Built |
| Hospital workspace registration | ❌ New |
| License verification | ❌ New |
| Location-based search | ❌ New |
| Queue tracking system | ❌ New |
| Patient profile CRUD | ❌ New |
| Doctor appointment dashboard | ❌ New |
| Global validation / error handling | ❌ New |

---

## Entity Redesign

### New Entity: `HospitalWorkspace`

```java
@Entity
@Table(name = "hospital_workspaces")
public class HospitalWorkspace {
    @Id @GeneratedValue(...)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String licenseNumber;         // e.g., HeFRA registration #

    private String licenseDocumentUrl;    // uploaded license scan

    @Column(nullable = false)
    private String address;               // full street address

    private Double latitude;              // geocoded
    private Double longitude;             // geocoded

    @Column(columnDefinition = "TEXT")
    private String specialties;           // JSON array or comma-separated

    private Integer capacity;             // approximate max patients

    private Integer consultationDuration; // avg minutes per consult (e.g., 20)

    private String phone;
    private String email;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus; // PENDING, APPROVED, REJECTED

    private LocalDateTime createdAt;

    // Getters, setters...
}
```

### New Entity: `HospitalAdmin`

```java
@Entity
@Table(name = "hospital_admins", uniqueConstraints = ...)
public class HospitalAdmin {
    @Id @GeneratedValue(...)
    private Long id;

    @ManyToOne
    private HospitalWorkspace hospitalWorkspace;

    @Column(nullable = false)
    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;           // BCrypt

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    private AdminRole role;            // PRIMARY_ADMIN, STAFF

    // Getters, setters...
}
```

### Modified: `Hospital` → becomes a reference under `HospitalWorkspace`

- Rename or keep as is but link to `HospitalWorkspace`
- Actually simplest path: **replace `Hospital` entity with `HospitalWorkspace`**. Update all `@ManyToOne` references in `Doctor`, `Department`, and `Booking` to point to `HospitalWorkspace` instead.

### Modified: `Department`

Add `parentDepartment` for hierarchy:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_department_id")
private Department parentDepartment;
```

### New Entity: `QueueEntry` (queue tracking)

```java
@Entity
@Table(name = "queue_entries")
public class QueueEntry {
    @Id @GeneratedValue(...)
    private Long id;

    @ManyToOne
    private Booking booking;

    private Integer queuePosition;      // computed on check-in

    private LocalDateTime checkInTime;  // when patient arrives at hospital

    @Enumerated(EnumType.STRING)
    private QueueStatus status;         // WAITING, IN_CONSULTATION, COMPLETED, SKIPPED

    private Integer estimatedWaitMinutes; // snapshot at check-in

    // Getters, setters...
}
```

### Modified: `Booking`

Add queue-related fields:

```java
private Boolean checkedIn = false;
private LocalDateTime checkInTime;
```

### Deleted/Removed Entity: `TimeSlot` (time-slot-per-doctor model)

The current `TimeSlot` model is per-doctor with fixed start/end times. The new model should generate slots dynamically from:
- Hospital working hours × consultation duration
- Or: doctor-specific schedule (if a doctor is assigned to a specific shift)

**Recommendation:** Keep `TimeSlot` but redesign it to work at the **department** level, auto-generated from the hospital's schedule config. Each slot = `department_id + date + start_time + end_time` that maps to one consultation window.

---

## Phase 1 — Hospital Workspace Registration

**Goal:** Hospitals can sign up, submit their details, get verified, and configure their workspace.

### Task 1.1: Create `HospitalWorkspace` entity and migration

- **Files:** `model/HospitalWorkspace.java`
- Add `VerificationStatus` enum: `PENDING, APPROVED, REJECTED`
- Add to `application.properties` or a config for file upload paths

### Task 1.2: Create `HospitalAdmin` entity

- **Files:** `model/HospitalAdmin.java`
- `AdminRole` enum: `PRIMARY_ADMIN, STAFF`
- One `HospitalWorkspace` can have multiple admins

### Task 1.3: License Verification Strategy

**Key decision: No public API exists for Ghana hospital license verification** (HeFRA / M&DC don't offer a public validation endpoint).

**Proposed approach — Hybrid Manual + Document Verification:**

1. Hospital admin submits: license number + upload scanned license document (PDF/image)
2. License number stored in DB with status `PENDING`
3. **Pulse admin** (super admin) reviews the document and approves/rejects via admin endpoint
4. On approval: workspace becomes `ACTIVE`, hospital admin receives notification
5. Rejection reason is stored for resubmission

**Future enhancement:** If HeFRA or Ghana Health Service ever exposes an API, add a validation service that checks license numbers programmatically.

**Entities needed:**
- `HospitalWorkspace.licenseDocumentUrl` — path to uploaded scan
- `HospitalWorkspace.verificationStatus` — PENDING → APPROVED/REJECTED
- `HospitalWorkspace.rejectionReason` — `@Column(nullable = true)`

### Task 1.4: Registration endpoint

- `POST /api/hospitals/register` — public endpoint
- Accepts: name, licenseNumber, licenseDocument (multipart), address, phone, email, specialties, capacity, consultationDuration
- Also creates the `PRIMARY_ADMIN` user in the same transaction
- Returns: workspace ID, admin credentials
- Files:
  - Create: `dto/HospitalRegistrationRequest.java`
  - Create: `dto/HospitalRegistrationResponse.java`
  - Create: `controller/HospitalWorkspaceController.java`
  - Create: `service/HospitalWorkspaceService.java`

### Task 1.5: License verification endpoint (admin-only)

- `POST /api/admin/hospitals/{id}/verify` — super admin
- Body: `{ "status": "APPROVED"|"REJECTED", "reason": "..." }`
- On approve: sets status to APPROVED
- On reject: sets status to REJECTED with reason
- **File upload service:** Store license PDFs in a `uploads/licenses/` directory (or cloud storage in future)

### Task 1.6: Workspace configuration endpoints

- `PUT /api/hospitals/{id}/departments` — create/update department hierarchy
- `PUT /api/hospitals/{id}/working-hours` — set weekly schedule
- `PUT /api/hospitals/{id}/doctors` — add doctors to department
- Protected by JWT + hospital admin role check

### Task 1.7: Update frontend considerations

- **Mobile (patient app):** No changes yet in Phase 1
- **Web (hospital admin dashboard):** Registration form → departments setup → doctor management → schedule config
- **API contract** should be shared with both frontend teams early

---

## Phase 2 — Patient Discovery & Hospital Matching

**Goal:** Patients can search/find hospitals by location proximity, specialty match, and availability.

### Task 2.1: Geocoding service

- Use OpenStreetMap Nominatim API (free) to geo-code hospital addresses on creation
- Create `LocationService` that:
  - Takes address string → returns lat/lng
  - Saves lat/lng on `HospitalWorkspace` during registration
  - Caches results to avoid repeated API calls
- **Files:**
  - Create: `service/LocationService.java`
  - Modify: `HospitalWorkspaceService.java` to call geocoding on registration

### Task 2.2: Nearby hospital search endpoint

- `GET /api/hospitals/nearby?lat=X&lng=Y&radius=KM&specialty=Z`
- Returns hospitals sorted by distance with:
  - Distance from user
  - Matched specialties
  - Current queue load (from queue system — Phase 3)
  - Capacity utilization
- **Distance calculation:**
  - MVP: Haversine formula in SQL (works without extensions)
  - Future: PostgreSQL PostGIS extension for proper spatial queries
- **Files:**
  - Create: `dto/HospitalSearchResult.java`
  - Modify: `HospitalWorkspaceController.java` / `BookingService.java`

### Task 2.3: Specialty matching

- Hospitals list their specialties (JSON array of strings)
- Patients search by specialty keyword or category
- `GET /api/hospitals?specialty=cardiology` — partial match on specialties field
- Future: normalize specialties into a lookup table

### Task 2.4: Hospital detail endpoint

- `GET /api/hospitals/{id}` — full profile for patient view
- Returns: name, location, specialties, capacity, consultation duration, doctors list (by department), current wait times

### Task 2.5: Frontend considerations

- **Mobile:** Map view showing nearby hospitals, list view with distance + specialty badges
- **Web:** No changes (admin dashboard already has full access)

---

## Phase 3 — Booking & Real-Time Queue System

**Goal:** Patients book appointments with hospitals and can track their real-time queue position.

### Task 3.1: Redesign booking flow

- Current: patient → doctor → time slot
- New: patient → hospital → department (optional) → available time → book
- `POST /api/bookings` now accepts:
  - `hospitalWorkspaceId`
  - `departmentId` (optional — null means general)
  - `doctorId` (optional — null means any available)
  - `patientId`
  - `date`
  - `preferredTime` (optional — will match to nearest slot)

### Task 3.2: Time slot generation

- Generate available slots from:
  - Hospital's working hours × consultation duration
  - Subtract already-booked slots
- `GET /api/hospitals/{id}/slots?date=YYYY-MM-DD&departmentId=X`
- Returns: list of `{ startTime, endTime, isAvailable }`

### Task 3.3: Queue tracking on check-in

- `PATCH /api/bookings/{id}/check-in` — patient marks arrival at hospital
  - Sets `checkedIn = true`, `checkInTime = now()`
  - Creates `QueueEntry` with computed position
- Queue position calculation:
  - Count all `QueueEntry` records for same hospital + date where:
    - `status = WAITING` (checked in but not yet seen)
    - Plus confirmed bookings for today that haven't checked in yet (to show wait if they arrive)
  - Position = your order in the sorted list (by check-in time or booking time)
- Estimated wait = `queuePosition × consultationDuration`

### Task 3.4: Real-time queue status endpoint

- `GET /api/hospitals/{id}/queue` — hospital staff view
- `GET /api/bookings/{id}/queue-status` — patient view
  - Returns: `{ queuePosition, estimatedWaitMinutes, peopleAhead, status }`
- Endpoint for doctors: `GET /api/doctors/me/queue` — see next patient

### Task 3.5: Queue advancement

- `PATCH /api/queue/{queueEntryId}/advance` — doctor/hospital staff marks patient as "in consultation"
  - Updates `QueueEntry.status` to `IN_CONSULTATION`
  - When consultation ends: status → `COMPLETED`
  - Automatically advances queue for all waiting patients (their position decreases)
- `PATCH /api/queue/{queueEntryId}/complete` — ends consultation
- `PATCH /api/queue/{queueEntryId}/skip` — patient not present

### Task 3.6: Frontend considerations

- **Mobile (patient):** "Check In" button when near hospital, live queue position widget with estimated wait, notifications when queue position changes
- **Web (hospital dashboard):** Live queue board showing all waiting patients, call-next button, patient details at a glance

---

## Cross-Cutting Concerns

### Task 4.1: Global validation & error handling

- Add `spring-boot-starter-validation` dependency
- Add `@Valid` + `@NotBlank` / `@Email` / `@Pattern` to all DTOs
- Create a global `@ControllerAdvice` exception handler
- **Files:**
  - Create: `config/GlobalExceptionHandler.java`
  - Modify: All DTOs to add validation annotations
  - Modify: All controllers to add `@Valid` on request bodies

### Task 4.2: Security — role-based access

Current JWT has roles `PATIENT`, `DOCTOR`. Add `HOSPITAL_ADMIN`, `SUPER_ADMIN`.

- Modify `JwtUtil` to encode role in token
- Modify `SecurityConfig` to enforce role-based access:
  - `/api/hospitals/*/departments` → `HOSPITAL_ADMIN` only
  - `/api/admin/*` → `SUPER_ADMIN` only
  - Patient endpoints → `PATIENT` only
  - Booking endpoints → mixed (patients create, hospital staff manage)
- Add annotation-based security (`@PreAuthorize`) for fine-grained control

### Task 4.3: Patient profile CRUD

- `GET /api/patients/me` — view profile
- `PUT /api/patients/me` — update: add dateOfBirth, gender, email, address
- `GET /api/patients/me/bookings` — appointment history
- These are currently missing — the patient model has the fields but no endpoints to set them after signup

### Task 4.4: Doctor appointment dashboard

- `GET /api/doctors/me/appointments?date=YYYY-MM-DD` — doctor's schedule for the day
- `GET /api/doctors/me/appointments/{id}` — single appointment detail
- `PATCH /api/doctors/me/appointments/{id}/notes` — add consultation notes

### Task 4.5: Database migration strategy

Since `ddl-auto=update` is used in dev, changes to entity definitions will auto-migrate. But for the `Hospital → HospitalWorkspace` rename:

1. Create `HospitalWorkspace` entity fresh
2. Write a migration script to copy existing hospital data into `HospitalWorkspace`
3. Update all FK references (`Doctor`, `Department`, `Booking`) to point to `HospitalWorkspace`
4. Drop `Hospital` table
5. **OR** simpler: keep `Hospital` as table name but add new fields to it (less disruptive)

**Recommendation:** Keep table name as `hospitals` for backward compatibility, but add all new fields and rename the entity class from `Hospital` to `HospitalWorkspace`. Use `@Table(name = "hospitals")` on the new entity.

---

## Risks & Tradeoffs

### Risk 1: License verification without an API
- **Problem:** No public Ghana healthcare license verification API exists
- **Mitigation:** 3-step verification process:
  1. Hospital uploads license number + scanned license document
  2. **SMS/email OTP** sent to registered business phone/email to prove ownership
  3. Pulse super admin reviews document + OTP status, then approves/rejects
- **Future:** Build an internal verification portal where Pulse staff review submissions

### Risk 2: Geocoding reliability
- **Problem:** Nominatim has usage limits (1 req/sec) and may fail for some Ghana addresses
- **Mitigation:** Cache geocoding results, allow manual lat/lng input on hospital registration
- **Fallback:** Display results without distance sort if geocoding fails

### Risk 3: Real-time queue with polling
- **Problem:** Without WebSocket/SSE, patients must poll for queue updates
- **Mitigation:** MVP uses polling every 30 seconds. Add server-sent events (SSE) as an upgrade path
- **Note:** No extra dependencies — Spring supports SSE natively via `SseEmitter`

### Risk 4: Specialties normalization
- **Problem:** Free-text specialties will give inconsistent matching
- **Mitigation:** Provide a curated list of specialties (dropdown) for hospitals to choose from, with an "other" option for custom entries
- **Source:** Use Ghana Health Service / WHO standard specialty categories

---

## Suggested Implementation Order

```
Phase 1 ──────────────────────────────────────────
  Task 1.1: HospitalWorkspace entity + VerificationStatus enum
  Task 1.2: HospitalAdmin entity + AdminRole enum + BCrypt auth
  Task 1.3: Registration endpoint (POST /api/hospitals/register)
  Task 1.4: Admin verification endpoint (POST /api/admin/hospitals/{id}/verify)
  Task 1.5: File upload service for license documents
  Task 1.6: Department hierarchy (parentDepartment field)
  Task 1.7: Doctor management under hospital workspace
  Task 1.8: Working hours & schedule configuration

Phase 2 ──────────────────────────────────────────
  Task 2.1: Geocoding service (Nominatim integration)
  Task 2.2: Nearby hospital search (GET /api/hospitals/nearby)
  Task 2.3: Specialty matching & search filters
  Task 2.4: Hospital detail endpoint (full profile)

Phase 3 ──────────────────────────────────────────
  Task 3.1: Redesign Booking flow (hospital-level booking)
  Task 3.2: Dynamic time slot generation
  Task 3.3: Check-in & QueueEntry creation
  Task 3.4: Queue status endpoints (patient view + hospital view)
  Task 3.5: Queue advancement (call-next, complete, skip)

Cross-cutting ────────────────────────────────────
  Task 4.1: Global validation + @ControllerAdvice
  Task 4.2: Role-based security (HOSPITAL_ADMIN, SUPER_ADMIN)
  Task 4.3: Patient profile CRUD endpoints
  Task 4.4: Doctor appointment dashboard endpoints
  Task 4.5: Database migration (Hospital → HospitalWorkspace)
```

---

## Database Schema (PostgreSQL)

This is the complete schema for all tables in Pulse v2. Relationships are shown via foreign keys.

### Table: `hospital_workspaces`

```sql
CREATE TABLE hospital_workspaces (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    license_number    VARCHAR(255) UNIQUE NOT NULL,
    license_document_url VARCHAR(500),
    address           VARCHAR(500) NOT NULL,
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,
    specialties       TEXT,                  -- JSON array, e.g. ["Cardiology","Pediatrics"]
    capacity          INTEGER,               -- approximate max patients hospital can handle
    phone             VARCHAR(50),
    email             VARCHAR(255),
    verification_status VARCHAR(50) DEFAULT 'PENDING'
                      CHECK (verification_status IN ('PENDING','APPROVED','REJECTED')),
    rejection_reason  TEXT,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Table: `hospital_admins`

```sql
CREATE TABLE hospital_admins (
    id                    BIGSERIAL PRIMARY KEY,
    hospital_workspace_id BIGINT NOT NULL REFERENCES hospital_workspaces(id),
    full_name             VARCHAR(255) NOT NULL,
    email                 VARCHAR(255) UNIQUE NOT NULL,
    password              VARCHAR(255) NOT NULL,  -- BCrypt hashed
    phone                 VARCHAR(50) NOT NULL,
    role                  VARCHAR(50) DEFAULT 'STAFF'
                          CHECK (role IN ('PRIMARY_ADMIN','STAFF'))
);
```

### Table: `patients`

```sql
CREATE TABLE patients (
    id          BIGSERIAL PRIMARY KEY,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    gender      VARCHAR(20) CHECK (gender IN ('MALE','FEMALE','OTHER')),
    email       VARCHAR(255),
    phone       VARCHAR(50) UNIQUE,
    ghana_card  VARCHAR(100) UNIQUE,
    password    VARCHAR(255) NOT NULL,       -- BCrypt hashed
    address     VARCHAR(500)
);
```

### Table: `departments`

```sql
CREATE TABLE departments (
    id                    BIGSERIAL PRIMARY KEY,
    hospital_workspace_id BIGINT NOT NULL REFERENCES hospital_workspaces(id),
    name                  VARCHAR(255) NOT NULL,
    parent_department_id  BIGINT REFERENCES departments(id),  -- nullable; for hierarchy
    description           TEXT,
    consultation_fee      DECIMAL(10,2),
    abbreviation          VARCHAR(20)                          -- used for workspace ID generation (e.g., "CARDIO")
);
```

### Table: `doctors`

```sql
CREATE TABLE doctors (
    id                    BIGSERIAL PRIMARY KEY,
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    specialization        VARCHAR(255),
    department_id         BIGINT REFERENCES departments(id),
    email                 VARCHAR(255),
    phone                 VARCHAR(50),
    license_number        VARCHAR(100) UNIQUE,
    workspace_id          VARCHAR(50) UNIQUE NOT NULL,  -- auto-generated: "CARDIO-DOC-001"
    password              VARCHAR(255) NOT NULL,        -- BCrypt hashed
    hospital_workspace_id BIGINT REFERENCES hospital_workspaces(id),
    consultation_duration INTEGER DEFAULT 20            -- minutes, per doctor
);
```

### Table: `time_slots`

Slots are generated at the **department** level (not per doctor) based on the hospital's working hours and consultation duration. Patients book a department + time; the hospital assigns the doctor later.

```sql
CREATE TABLE time_slots (
    id            BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL REFERENCES departments(id),
    date          DATE NOT NULL,
    start_time    TIME NOT NULL,
    end_time      TIME NOT NULL,
    is_booked     BOOLEAN DEFAULT FALSE,

    UNIQUE (department_id, date, start_time)  -- prevent duplicate slots
);
```

### Table: `bookings`

```sql
CREATE TABLE bookings (
    id                    BIGSERIAL PRIMARY KEY,
    patient_id            BIGINT NOT NULL REFERENCES patients(id),
    doctor_id             BIGINT REFERENCES doctors(id),         -- assigned by hospital admin later
    department_id         BIGINT NOT NULL REFERENCES departments(id),
    hospital_workspace_id BIGINT NOT NULL REFERENCES hospital_workspaces(id),
    time_slot_id          BIGINT REFERENCES time_slots(id),
    booking_date          TIMESTAMP NOT NULL,
    status                VARCHAR(50) DEFAULT 'PENDING_PAYMENT'
                          CHECK (status IN ('PENDING_PAYMENT','CONFIRMED','CANCELLED')),
    payment_status        VARCHAR(50) DEFAULT 'PENDING'
                          CHECK (payment_status IN ('PENDING','PAID','FAILED')),
    amount_due            DECIMAL(10,2) NOT NULL,
    checked_in            BOOLEAN DEFAULT FALSE,
    check_in_time         TIMESTAMP
);
```

### Table: `queue_entries`

Tracks the real-time queue for each hospital/department/date. Queue position is computed on check-in based on booking order.

```sql
CREATE TABLE queue_entries (
    id                      BIGSERIAL PRIMARY KEY,
    booking_id              BIGINT UNIQUE NOT NULL REFERENCES bookings(id),
    queue_position          INTEGER,                     -- computed on check-in
    check_in_time           TIMESTAMP NOT NULL,
    status                  VARCHAR(50) DEFAULT 'WAITING'
                            CHECK (status IN ('WAITING','IN_CONSULTATION','COMPLETED','SKIPPED')),
    estimated_wait_minutes  INTEGER                      -- snapshot at check-in
);
```

### Table: `working_hours`

Hospital admin defines the weekly schedule per hospital.

```sql
CREATE TABLE working_hours (
    id                    BIGSERIAL PRIMARY KEY,
    hospital_workspace_id BIGINT NOT NULL REFERENCES hospital_workspaces(id),
    day_of_week           VARCHAR(10) NOT NULL
                          CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    open_time             TIME,        -- NULL = closed that day
    close_time            TIME,        -- NULL = closed that day

    UNIQUE (hospital_workspace_id, day_of_week)
);
```

### Table: `patient_doctors` (join table)

```sql
CREATE TABLE patient_doctors (
    patient_id BIGINT NOT NULL REFERENCES patients(id),
    doctor_id  BIGINT NOT NULL REFERENCES doctors(id),
    PRIMARY KEY (patient_id, doctor_id)
);
```

### Entity Relationship Summary

```
hospital_workspaces 1──N hospital_admins
hospital_workspaces 1──N departments
hospital_workspaces 1──N doctors
hospital_workspaces 1──N bookings
hospital_workspaces 1──N working_hours
departments N──1 departments  (parentDepartment — self-referencing hierarchy)
departments    1──N doctors
departments    1──N time_slots
departments    1──N bookings
doctors        1──N bookings       (assigned by admin, nullable)
doctors        N──M patients        (via patient_doctors join table)
patients       1──N bookings
time_slots     1──1 bookings        (one slot per booking)
bookings       1──1 queue_entries   (one queue entry per booking)
```

---

## Team Decisions (Confirmed)

| # | Question | Decision |
|---|---|---|
| 1 | License verification | Document upload + **SMS/email OTP** proof of ownership + super admin review |
| 2 | Consultation duration | **Per doctor** in a department (patients book a cardiologist, not just cardiology) |
| 3 | Booking specificity | **Per department only** — hospital assigns to available doctor |
| 4 | Queue position logic | **Booking-order based** (position = order of booking, not check-in arrival) |
| 5 | Specialties | **Curated dropdown list** of ~20–30 standard specialties for consistent matching |