# Pulse — Role-Scoped Endpoints

**Status:** ✅ READY TO BUILD

**Proposed by:** Psam
**Date:** 2026-06-23

---

## Summary

Pulse has 24 endpoints covering hospital registration, patient auth, booking, and license management. But three critical gaps exist in **role scoping**:

1. **No "my bookings" endpoint** — patients can't list their own bookings; doctors can't see their schedule.
2. **No role-based access control on GET `/api/bookings/{id}`** — any authenticated user can view any booking, even one that isn't theirs.
3. **No pagination** — list endpoints return everything unfiltered. When a hospital has 10,000 bookings or a doctor has 500 patients, it'll break.

---

## Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Patient "my bookings" | `GET /api/patients/me/bookings` | Cleanest REST, easy to secure, extensible for future patient endpoints |
| Doctor schedule | `GET /api/doctors/me/appointments` | Symmetrical with patient pattern, maps to domain language |
| Scoping `GET /api/bookings/{id}` | **Service-layer check** | Single source of truth, SUPER_ADMIN bypass is trivial |
| Pagination | **Spring Pageable** | Zero config, built-in JPA support, rich query features |
| Build order | **Step 1: Security + Pagination — Step 2: Patient & Doctor in parallel** | Fix the hole first, share the pagination utility, then build both role endpoints simultaneously |

---

## Build Plan

### ✅ Step 1: Scoping + Pagination Foundation — COMPLETE

#### 1a — Role-scoped `GET /api/bookings/{id}` ✅

**Changes made:**
- `BookingService.getBookingSummary()` now accepts `(Long bookingId, Long authenticatedUserId, String role)` — PATIENT sees own, DOCTOR sees own, SUPER_ADMIN sees all, others (HOSPITAL_ADMIN) pass through
- `BookingController.getBookingSummary()` extracts userId + role from `SecurityContextHolder.getContext().getAuthentication()` and passes to service
- `AccessDeniedException` thrown for unauthorized access → caught by `GlobalExceptionHandler` → 403 JSON response

**Files modified:**
- `service/BookingService.java` — ownership check logic
- `controller/BookingController.java` — auth context extraction

#### 1b — Pagination on list endpoints ✅

**Changes made:**
- `BookingRepository` — added `findByPatientId(Long, Pageable)`, `findByDoctorId(Long, Pageable)`
- `HospitalRepository` — added `findAll(Pageable)`
- `DepartmentRepository` — added `findByHospitalId(Long, Pageable)`
- `DoctorRepository` — added `findByDepartmentId(Long, Pageable)`
- `BookingService` — added paginated overloads for `listHospitals`, `listDepartments`, `listDoctors`
- `HospitalController.listHospitals()` — accepts `@PageableDefault(size=20)`
- `DepartmentController.listDepartments()` — accepts `@PageableDefault(size=20)`
- `DoctorController.listDoctors()` — accepts `@PageableDefault(size=20)`
- All endpoints return `Page<T>` instead of `List<T>`

**Files modified:**
- `repository/BookingRepository.java`
- `repository/HospitalRepository.java`
- `repository/DepartmentRepository.java`
- `repository/DoctorRepository.java`
- `service/BookingService.java`
- `controller/HospitalController.java`
- `controller/DepartmentController.java`
- `controller/DoctorController.java`

### ✅ Step 2: Patient & Doctor Endpoints — COMPLETE

#### 2a — Patient "my bookings" (`GET /api/patients/me/bookings`) ✅

**Changes made:**
- Created `PatientController` at `/api/patients` with `@PreAuthorize("hasRole('PATIENT')")`
- `GET /me/bookings` extracts patient ID from JWT `sub` claim → queries `BookingRepository.findByPatientId()` → returns `Page<BookingResponse>`
- Paginated with `@PageableDefault(size=20)`

**Files created:**
- `controller/PatientController.java` — **new file**

#### 2b — Doctor schedule (`GET /api/doctors/me/appointments`) ✅

**Changes made:**
- Created `DoctorScheduleController` at `/api/doctors` with `@PreAuthorize("hasRole('DOCTOR')")`
- `GET /me/appointments` extracts doctor ID from JWT `sub` claim → queries `BookingRepository.findByDoctorId()` → returns `Page<BookingResponse>`
- Paginated with `@PageableDefault(size=20)`

**Files created:**
- `controller/DoctorScheduleController.java` — **new file**

#### Service support

**Changes made to `BookingService`:**
- Added `listPatientBookings(Long patientId, Pageable pageable)` — uses `.map(this::toResponse)` for DTO conversion
- Added `listDoctorAppointments(Long doctorId, Pageable pageable)` — same pattern

## Acceptance Criteria

| File | What | Type |
|------|------|------|
| `service/BookingService.java` | Add ownership check + paginated queries | Modify |
| `controller/BookingController.java` | Extract auth context, pass to service | Modify |
| `controller/PatientController.java` | **New** — `GET /me/bookings` | Create |
| `controller/DoctorScheduleController.java` | **New** — `GET /me/appointments` | Create |
| `repository/BookingRepository.java` | Add `findByPatientId(.., Pageable)`, `findByDoctorId(.., Pageable)` | Modify |
| `repository/DoctorRepository.java` | Add `Pageable` variants if needed | Modify |
| `controller/HospitalController.java` | Add `Pageable` to list endpoints | Modify |
| `controller/DepartmentController.java` | Add `Pageable` | Modify |
| `controller/DoctorController.java` | Add `Pageable` | Modify |
| `controller/TimeSlotController.java` | Add `Pageable` | Modify |
| `dto/` | Possibly a paginated wrapper DTO | Maybe |
| `test/` | New test files for all changes | Create |

---

## Acceptance Criteria

- [ ] `GET /api/bookings/{id}` returns 200 for the booking's owner (patient or doctor)
- [ ] `GET /api/bookings/{id}` returns 403 for a non-owner
- [ ] `GET /api/bookings/{id}` returns 200 for SUPER_ADMIN regardless of ownership
- [ ] `GET /api/patients/me/bookings` returns that patient's bookings only, paginated
- [ ] `GET /api/patients/me/bookings` returns 403 for DOCTOR or HOSPITAL_ADMIN tokens
- [ ] `GET /api/doctors/me/appointments` returns that doctor's appointments only, paginated
- [ ] `GET /api/doctors/me/appointments` returns 403 for PATIENT tokens
- [ ] All list endpoints accept `?page=0&size=20&sort=bookingDate,desc` and return page metadata
- [ ] Existing public discovery endpoints still work without auth
- [ ] Existing seed data + tests still pass

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| **JWT `sub` claim might be patient ID for some roles and doctor/admin ID for others** | Verified: all tokens use `subject(userId.toString())` where userId is the entity's DB primary key. PATIENT and DOCTOR IDs live in different tables, so a patient's ID will never collide with a doctor's ID. The role check on each endpoint prevents misuse. |
| **Spring Pageable response format breaks frontend** | It's the standard Spring format — the frontend team either already handles it or can adapt easily. Offset-based pagination is fine at Pulse's scale. |
| **Adding pagination to all public endpoints at once is scope creep** | We start with the new endpoints (patient bookings, doctor schedule) which get pagination by design. We optionally extend pagination to existing public endpoints in 2c. |

---

## Open Questions (answered)

- [x] ~~Should patient `GET /me/bookings` include past bookings, future only, or all?~~ → **All bookings, sorted by date descending.** Mobile can filter client-side if needed. Future: add `?status=CONFIRMED` query filter.
- [x] ~~Should doctors see only upcoming appointments or a full history?~~ → **All appointments (past + future).** Add `?status=CANCELLED,CONFIRMED` filtering later if needed.
- [x] ~~What page size is reasonable for mobile clients vs web?~~ → **Default 20.** Spring Pageable allows client override via `?size=N`. Mobile uses smaller screens so larger pages waste bandwidth.
- [x] ~~Do we need filtering by status on list endpoints?~~ → **Not now.** Add when requested. The booking list page on the frontend can filter client-side for the MVP.
