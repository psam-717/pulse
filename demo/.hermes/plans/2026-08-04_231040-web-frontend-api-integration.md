# Pulse Web Frontend API Integration — Audit & Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Make the Pulse Spring Boot backend (`psam-717/pulse`, `D:\Projects\pulse\backend\demo`) satisfy the `pulse-web` frontend contract (`housebuoy/pulse-web`, `D:\Projects\pulse\web-frontend`) so the dashboard can flip `NEXT_PUBLIC_USE_MOCK=false` and run fully against real APIs.

**Architecture:** The frontend already has a clean mock↔real swap layer (`lib/api/*.ts`). The backend must grow a **facility-plane API** (staff users, 5 roles, `facilityId`-scoped multi-tenancy) alongside the existing patient/hospital APIs, implementing the endpoint contract in `BACKEND_SPEC.md` (§6) — endpoint shapes, auth model (§2.3, §8), and business rules (§7). Work is phased by dependency: auth vertical slice first, then domains, then the tenant lifecycle.

**Tech stack:** Spring Boot 4.0.4 + Java 25 + PostgreSQL (`pulse_db`, `ddl-auto=update`) · Next.js 16 + React 19 + TanStack Query + axios · JJWT 0.12.x · springdoc OpenAPI

---

## 1. Audit — Frontend (`pulse-web`)

### 1.1 Architecture (verified in code)

```
components/hooks → hooks/use-*.ts (TanStack Query) → lib/api/*.ts (swap point) → lib/mock/*.ts | axios → Spring Boot
```

- **Swap point:** every `lib/api/*.ts` function checks `USE_MOCK = process.env.NEXT_PUBLIC_USE_MOCK !== "false"` (mock is **default**). Setting the env var to `"false"` makes the same functions hit `http://localhost:8080/api` (default `NEXT_PUBLIC_API_URL`).
- **Axios** (`lib/axios.ts`): attaches `Authorization: Bearer <localStorage.pulse_token>` on every request. 401 redirect logic is **commented out** (placeholder — must be finished).
- **Auth is mock-only:** no `lib/api/auth.ts` exists. `login()`/`verifyLoginOtp()`/`resolveSession()` live in `lib/mock/auth.ts`. `WorkspaceSession` **already carries `facilityId`** (newer than the spec's §5.1 snapshot).
- **No `middleware.ts`** — `/d` and `/w` are guarded client-side by `RequireRole` (UX only; real enforcement is the backend's job, per the code's own comments).
- **No `.env.local`** exists yet (`.env*` gitignored).
- **No create endpoints** for appointments or queue entries exist in the frontend (booking comes from the mobile app; queue check-in is a design gap — spec §10.1).
- **One real frontend bug** (spec §10.3): `clinical-record-dialog.tsx` sends `medications`, the type/API expect `currentMedications`.

### 1.2 The contract document

`BACKEND_SPEC.md` (2,022 lines) is the authoritative contract: §6 endpoint table (all paths, methods, bodies, responses), §5 types verbatim, §7 business rules (appointment state machine, queue priority/severity, atomic call-next), §8 auth/RBAC requirements, §9 non-obvious requirements (polling cadences, image upload, idempotency), §10 open questions. **The backend must grow to fit this spec — not the other way around.**

---

## 2. Audit — Backend (`pulse`)

### 2.1 What exists today (11 controllers)

| Domain | Endpoints (Spring Boot) | Notes |
|---|---|---|
| Auth (patient) | `POST /api/auth/patient/signup`, `/verify-otp`, `/login` | OTP signup, dual identifier (phone/Ghana Card) |
| Auth (admin/doctor) | `POST /api/auth/admin/create-doctor`, `/login` | workspaceId+email+password |
| Hospitals | `POST /api/hospitals/register`, `/login`, `GET /{id}`, `GET /{id}/departments`, `POST /{id}/departments`, `DELETE /{id}/departments/{deptId}`, `PUT/GET /{id}/working-hours`, `POST/GET /{id}/license` | license upload + super-admin verify |
| Departments | `GET /api/departments/{id}/doctors` | minimal |
| Doctors | `GET /api/doctors/{id}/slots`, `GET /api/doctors/me/appointments` | time slots, own appointments |
| Bookings | `POST /api/bookings`, `GET /{id}`, `PATCH /{id}/payment`, `DELETE /{id}` | patient-facing booking w/ validation |
| Patients | `GET /api/patients/me/bookings` | no patient list/CRUD |
| Admin | `PUT /api/admin/hospitals/{id}/verify` | super admin license verify |
| Health | `GET /api/hello`, `/api/status` | public |

### 2.2 Entity model & auth

- **Entities:** `Hospital`, `Department`, `Doctor`, `Patient`, `Booking`, `TimeSlot`, `WorkingHours`, `HospitalAdmin`, `PendingRegistration`
- **Roles (JWT claim):** `PATIENT`, `DOCTOR`, `HOSPITAL_ADMIN`, `SUPER_ADMIN` — 4 roles, **no staff model, no nurse/front-desk/read-only**
- **JWT claims:** `sub=userId`, `role`, `hospitalId` (admins; `0` for super admin). 24h expiry. **No `facilityId` claim name, no `aud` plane separation.**
- **DB:** PostgreSQL `pulse_db`, `ddl-auto=update`, single tenant (no RLS/filter)
- **Tests:** only `DemoApplicationTests` (context load). No per-domain tests.

### 2.3 Field-level mapping (backend entity → frontend type)

| Frontend type | Backend entity | Gap |
|---|---|---|
| `FacilityProfile` (§5.8) | `Hospital` | name→hospitalName, address/region, hefraLicense=licenseNumber, specialties (JSON string→array), **status lifecycle missing (pending/active_pending_docs/suspended)** |
| `Department` (§5.3) | `Department` | +`code` (from abbreviation), `status`, `headDoctorName`, `doctorsOnDuty`, `totalDoctors`, `rooms`, `opensAt`/`closesAt`/`twentyFourSeven`; `waiting`/`inConsultation`/`avgWaitMinutes`/`appointmentsToday` are server-derived (§10.2) |
| `StaffMember` (§5.6) | `Doctor` + `HospitalAdmin` | unify into one `StaffMember` model; 5 roles; `shiftStart/End`, `dutyStatus`, `accountStatus` |
| `Appointment` (§5.2) | `Booking` | vocabulary + shape: reference, scheduledAt (from TimeSlot), durationMinutes, status state machine (§7.1); **checked_in hand-off to queue** |
| `Patient` (§5.5) | `Patient` | +`patientNumber` (PT-00001), `name` (first+last), `allergies[]`, `currentMedications[]`, `latestVitals`, `currentVisit` |
| `QueueEntry`/`QueueDepartment` (§5.4) | **none** | net-new |
| `Notification` (§5.7) | **none** | net-new |
| Settings (§5.8, 21 endpoints) | partial (`WorkingHours`, license) | net-new mostly |
| Dashboard/Analytics (§5.9–5.10) | **none** | net-new aggregation |

---

## 3. Gap analysis — endpoint coverage matrix (spec §6 → backend)

Legend: ✅ exists & compatible · 🔶 partial (shape/scope differs) · ❌ missing · 🆕 design-gap (spec says frontend lacks it too — must design)

| # | Endpoint (frontend path) | Status | Backend action |
|---|---|---|---|
| **Auth** | | | |
| 1 | `POST /api/auth/login` (email+password → `{session, token}`) | 🔶 | `POST /api/hospitals/login` exists but returns `{token,role,userId,message}`; need staff login resolving admin/doctor → `WorkspaceSession` + token w/ `facilityId` |
| 2 | `GET /api/auth/me` → `CurrentUser` | ❌ | new; unify with `WorkspaceSession` (§10.8) |
| **Appointments** | | | |
| 3 | `GET /appointments` (date/dept/status **or** from/to) | ❌ | new; one path, two param shapes (§6.1 note); bridge from `Booking` |
| 4 | `GET /appointments/stats` | ❌ | new |
| 5 | `GET /appointments/departments` | ❌ | new (4-dept light list) |
| 6 | `PATCH /appointments/{id}` `{status}` | ❌ | new; **state machine §7.1**, checked_in→queue hand-off (§10.1) |
| 7 | `POST /appointments` | 🆕 | frontend has none; **mobile `POST /bookings` feeds this domain** — decide bridge |
| **Departments** | | | |
| 8 | `GET /departments` | 🔶 | exists only as `GET /hospitals/{id}/departments`; add facility-scoped list w/ new shape |
| 9 | `GET /departments/stats` | ❌ | new |
| 10 | `POST /departments` | 🔶 | exists (hospital-scoped); align body to `CreateDepartmentInput` |
| 11 | `PATCH /departments/{id}` | ❌ | new |
| 12 | `PATCH /departments/{id}/head-doctor` | ❌ | new |
| 13 | `DELETE /departments/{id}` | 🔶 | exists (hospital-scoped); add `canDelete` server gate (409) §7.4 |
| **Live Queue** | | | |
| 14 | `GET /queue/departments` | ❌ | new (severity thresholds §7.2) |
| 15 | `GET /queue/entries` | ❌ | new (5s polling; waiting/in_consultation only) |
| 16 | `POST /queue/call-next` | ❌ | new; **atomic** (§7.3), return called entry, derive clinician from session |
| 17 | `PATCH /queue/entries/{id}` | ❌ | new; validate transitions §7.3 |
| 18 | `POST /queue/entries` (check-in) | 🆕 | design gap — must design |
| **Patients** | | | |
| 19 | `GET /patients` | 🔶 | new list (entity exists); 15s polling |
| 20 | `GET /patients/{id}` | ❌ | new (defined but unused in FE — keep for parity) |
| 21 | `POST /patients` | 🔶 | entity exists; new create + `patientNumber` generation (concurrency-safe, §10.3) |
| 22 | `PATCH /patients/{id}` | ❌ | new |
| 23 | `PATCH /patients/{id}/clinical-record` | ❌ | new; **field-name bug on FE** (`medications` vs `currentMedications`) |
| 24 | `POST /patients/{id}/vitals` | ❌ | new; store-only, no interpretation (§7.5) |
| **Staff** | | | |
| 25 | `GET /staff` | 🔶 | `Doctor` entity exists; new unified staff list |
| 26 | `GET /staff/{id}` | ❌ | new |
| 27 | `POST /staff` | 🔶 | `create-doctor` exists; align to `CreateStaffInput` |
| 28 | `PATCH /staff/{id}` | ❌ | new; also deactivate/activate path §7.6 |
| **Notifications** | | | |
| 29 | `GET /notifications`, `GET /notifications/unread-count` | ❌ | new |
| 30 | `PATCH /notifications/{id}/read`, `POST /notifications/read-all` | ❌ | new; trigger logic unspecified (§10.5) |
| **Settings** (21 endpoints, §6.7) | | | |
| 31 | `GET/PATCH /settings/facility` | 🔶 | hospital exists; add shape + **server-side status transition** (HeFRA) §4.6 |
| 32 | `GET/PATCH /settings/profile` | 🔶 | `HospitalAdmin` exists; shape differs |
| 33 | `POST /settings/profile/change-password` | ❌ | new |
| 34 | `GET/PATCH /settings/operational` | 🔶 | `WorkingHours` ≈ operational; full shape new |
| 35 | `GET/DELETE /settings/sessions`, `DELETE /settings/sessions/{id}` | ❌ | new (session mgmt) |
| 36 | `GET/PATCH /settings/2fa` | ❌ | new (bool toggle; enforcement later, §10.7) |
| 37 | `GET/PATCH /settings/preferences` | ❌ | new |
| 38 | `GET/POST /settings/account-request` | ❌ | new (90-day retention copy — Ghana DPA Act 843 §8.3) |
| 39 | `GET/POST /settings/invites`, `DELETE /settings/invites/{id}` | ❌ | new; unlinked from StaffMember (§10.4) |
| 40 | `GET/PATCH /settings/permissions` | ❌ | new; matrix is **backend-enforced** (§8.2) |
| **Dashboard** | | | |
| 41 | `GET /dashboard/stats`, `/queue`, `/alerts`, `/patient-volume` | ❌ | new aggregation (§7.8 — nothing to copy, build fresh) |
| 42 | `GET /facility/current` | 🔶 | hospital GET exists; shape differs |
| **Analytics** | | | |
| 43 | `GET /analytics?from&to` | ❌ | new; single combined payload §6.9; real status counts (not mock ratios §7.7) |
| **Tenant lifecycle / platform** | | | |
| 44 | `POST /public/access-requests` | ❌ | new; public, rate-limited (§4.3) |
| 45 | `PATCH /platform/facilities/{id}/approve|reject|suspend|reactivate`, `GET /platform/facilities*` | ❌ | new; **metadata-only, no clinical data** (§3.3) |
| 46 | HeFRA deadline job (suspends `active_pending_docs` past due) | ❌ | new backend scheduled job (§4.6) |

**Tally: 3 ✅/🔶-compatible, ~24 🔶 partial, ~17 ❌ net-new, 4 🆕 design-gaps.** This is a v3-scale backend phase, not a flag flip.

---

## 4. Cross-cutting architecture decisions (recommendations)

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D1 | **Two-plane auth** | Keep patient auth (mobile) as-is; add **facility-plane** staff auth (email+password → token w/ `facilityId` + `role` from `StaffRole`); reserve `aud` claim (`pulse-facility` vs legacy) | Spec §2.3–2.4; mobile (patient) and web (staff) are different planes |
| D2 | **Staff model** | New `StaffMember` entity (id, name, role∈{admin,doctor,nurse,front-desk,read-only}, title, departmentId, email, phone, shiftStart/End, dutyStatus, accountStatus, password, facilityId). Migrate `Doctor`→`StaffMember` (doctor rows become role=doctor); keep `Doctor` for patient-facing booking for now or unify | Spec §5.6; the permission matrix keys on these 5 roles |
| D3 | **Multi-tenancy enforcement** | `facilityId` from JWT on every facility-plane request; enforce via **Hibernate filter or base repository** (single choke point), never per-query convention; `facilityId` never client-settable (§2.1) | Spec §2.2 — leak = breach |
| D4 | **Booking→Appointment bridge** | `POST /bookings` (mobile) creates an `Appointment` (status `scheduled`) in the facility plane; web `PATCH /appointments/{id}` drives the state machine | Spec §10.1; both planes share the record |
| D5 | **Department derived fields** | `waiting`/`inConsultation`/`avgWaitMinutes`/`appointmentsToday` computed live from Queue/Appointment tables, not stored | Spec §10.2; nothing client-side can set them |
| D6 | **checked_in → queue hand-off** | `PATCH /appointments/{id}` with `status:"checked_in"` synchronously creates a `QueueEntry` (source=`appointment`, priority from appointment, patientId) in the same transaction | Spec §10.1 |
| D7 | **Analytics** | True volume-weighted means / real percentiles + real status counts from records; keep single combined `/analytics` payload | Spec §7.7/§10.8 — mock ratios are placeholder math |
| D8 | **Error envelope** | Standardize on `ApiResponse`-style `{status, message, errors[]}` for all facility-plane endpoints (backend already has this pattern) | Spec §10.9 |
| D9 | **Login response** | Facility login returns `{token, session: WorkspaceSession}` (add `session` alongside existing `token/role/userId/message`); add `GET /auth/me` returning full session | FE login page expects `LoginResult{session, token}` |
| D10 | **2FA** | Ship `{enabled}` toggle (settings surface) now; actual challenge flow deferred (spec §10.7) | Nothing extractable; don't block integration |

**Open questions to confirm with team before Phase 3+** (from spec §10): suspended→active recovery UX (§4.6/§10.10), HeFRA-during-onboarding behavior (§10.10), billing model (§10.10), multi-facility staff (§10.10), notifications trigger rules (§10.5), `capacity`/`duration` string-vs-number (§10.6), vitals history depth (§10.3).

---

## 5. Phased implementation plan

**Conventions:** every phase = feature branch (`feat/<phase-slug>` on backend), conventional commits, PR against `main`, test before commit. Backend patterns follow the `spring-boot-jwt-auth` skill (JWT, SecurityConfig, `@PreAuthorize`, DataSeeder, ApiResponse, GlobalExceptionHandler).

### Phase 0 — Baseline & verification (½ day)

**Objective:** both apps run; CORS works; the swap mechanism is proven.

**Backend:**
- Add CORS for `http://localhost:3000` in `config/SecurityConfig.java` (or `WebConfig`) — `allowedOrigins`, `allowedMethods`, `allowedHeaders`, `allowCredentials(false)` (Bearer, no cookies).
- Verify `./start.bat` boots, Swagger at `/swagger-ui.html`.

**Frontend:**
- Create `.env.local`: `NEXT_PUBLIC_USE_MOCK=false`, `NEXT_PUBLIC_API_URL=http://localhost:8080/api`.
- Finish the 401 interceptor in `lib/axios.ts` (clear `pulse_token`, redirect `/login`).

**Verify:** `npm run dev` on :3000, backend on :8080; confirm axios reaches backend (a request to `/api/status` from browser devtools).

### Phase 1 — Auth vertical slice (first real integration, 1–2 days)

**Objective:** staff can log in against the real backend; session resolves from JWT; `/d` vs `/w` routing works. This proves the entire pipeline before any domain work.

**Backend — new/modified files:**
- `model/StaffRole.java` (enum: ADMIN, DOCTOR, NURSE, FRONT_DESK, READ_ONLY) + `model/StaffMember.java` (per D2, minimal fields for login: id, name, role, email, password, facilityId, departmentId/name, title, specialty)
- `repository/StaffMemberRepository.java` — `Optional<StaffMember> findByEmailAndFacilityId(...)`
- `dto/LoginRequest.java` (`email`, `password`), `dto/LoginResponse.java` (`token`, `role`, `userId`, `message`, `session`)
- `service/StaffAuthService.java` — verify BCrypt, issue JWT with `sub=staffId`, `role`, `facilityId`, `aud="pulse-facility"`
- `controller/StaffAuthController.java` — `POST /api/auth/login`, `GET /api/auth/me`
- Modify `config/JwtUtil.java` — `generateStaffToken(staffId, facilityId, role)`
- Modify `config/JwtAuthFilter.java` — resolve staff + legacy tokens; principal = staffId
- Modify `config/SecurityConfig.java` — permit `POST /api/auth/login`; keep patient/hospital auth untouched
- `config/DataSeeder.java` — seed 2 staff members matching the FE demo sessions (`sarah.jenkins@knust-hospital.test`, `owusu@pulsehealth.test`, password `Password123!`)

**Frontend — files:**
- Create `lib/api/auth.ts` — `login()`, `verifyLoginOtp()`, `fetchMe()` with the `USE_MOCK` swap pattern (identical signatures to `lib/mock/auth.ts`)
- Modify `lib/mock/auth.ts` → keep as mock fallback; login page calls `lib/api/auth.ts`
- Modify `hooks/use-workspace-session.ts` → resolve session from `GET /auth/me` when real API (fallback to stored token decode)
- `app/(auth)/login/page.tsx` — minimal change: import from `lib/api/auth` instead of mock

**Verify:** login as admin → lands `/d` with real data load (department list will still be mock if Phase 2 pending — acceptable, session is real); login as doctor → `/w`. Bad password → friendly 401 message (checks the finished interceptor).

### Phase 2 — Departments & Staff (2–3 days)

**Objective:** `GET/POST/PATCH/DELETE /departments*` and `GET/POST/PATCH /staff*` per spec §6.2/§6.5; 5-role RBAC via `@PreAuthorize`.

**Backend:**
- Extend `Department` entity: `code`, `status`, `headDoctorName`, `rooms`, `opensAt`, `closesAt`, `twentyFourSeven` (+ derived fields per D5)
- `controller/DepartmentController.java` — facility-scoped CRUD per §6.2; `canDelete` gate → 409; close/archive zeroes `doctorsOnDuty/waiting/inConsultation` (§7.4)
- `controller/StaffController.java` — `GET/POST/PATCH /staff` per §6.5; deactivate = `accountStatus` flip via PATCH (§7.6)
- `@PreAuthorize` role checks against the §5.8 permission matrix (edit/view per resource — matrix enforced server-side)
- `dto/DepartmentRequest.java`, `dto/DepartmentResponse.java`, `dto/CreateStaffRequest.java`, `dto/StaffResponse.java` — match FE types exactly (spec §5.3/§5.6)

**Frontend:** no component changes needed (swap layer only). Verify departments/staff pages load real data.

**Verify:** Swagger + Postman: CRUD department, create staff, deactivate staff; wrong-role → 403 JSON.

### Phase 3 — Appointments bridge + state machine (2–3 days)

**Objective:** `GET/PATCH /appointments*` (§6.1) fed by existing `Booking` data; status state machine (§7.1); checked_in→queue hand-off (D6).

**Backend:**
- `Appointment` view/projection over `Booking` (+ `TimeSlot` for scheduledAt/duration); `reference` = `APT-####`
- `controller/AppointmentController.java` — `GET /appointments` (dual param shapes), `/stats`, `/departments`, `PATCH /{id}`
- `service/AppointmentService.java` — transition validation (scheduled→confirmed/cancelled, confirmed→checked_in/no_show, checked_in→confirmed undo); checked_in creates `QueueEntry` (D6) — Queue tables come in Phase 5; either stub the queue write or build minimal `QueueEntry` here
- Order: implement queue tables **first** if hand-off is in this phase (small reorder: Phase 5 queue model, Phase 3 uses it)

**Verify:** create booking via mobile endpoint → appears in `GET /appointments`; PATCH through legal transitions; illegal transition → 409.

### Phase 4 — Patients clinical (2 days)

**Objective:** patient list/CRUD + clinical record + vitals (§6.4, §5.5).

**Backend:**
- Extend `Patient`: `patientNumber` (PT-#####, DB sequence/unique), `allergies[]`, `currentMedications[]` (element collections), `latestVitals` (embedded, overwrite semantics §7.5)
- `controller/PatientController.java` — `GET /patients` (full list, 15s polling), `GET/POST/PATCH /patients/{id}`, `PATCH /{id}/clinical-record`, `POST /{id}/vitals`
- No interpretation/flagging of clinical data (normative constraint §7.5)

**Frontend fix (in this phase):** `components/dashboard/patients/clinical-record-dialog.tsx` — rename `medications` → `currentMedications` (spec §10.3, confirmed bug).

### Phase 5 — Live Queue (2–3 days) ⚠️ concurrency-critical

**Objective:** queue domain per §6.3 with atomic call-next (§7.3).

**Backend:**
- `model/QueueEntry.java` (id, ticketNumber A-###, patientId, patientName, departmentId, status, priority, source, checkInAt, calledAt, clinician, room)
- `repository/QueueRepository.java` + ticket-number generator (DB sequence, concurrency-safe)
- `controller/QueueController.java` — `GET /queue/departments` (severity: >40 critical, >25 warning), `GET /queue/entries?departmentId`, `POST /queue/call-next`, `PATCH /queue/entries/{id}`, `POST /queue/entries` (check-in — design-gap, minimal version)
- **Atomicity:** `UPDATE ... WHERE id=? AND status='waiting'` + row lock in one transaction; `call-next` = pick-top + lock atomically; return the called entry (or 409); validate status transitions §7.3
- Polling read-amplification: mutations invalidate both `/queue/entries` and `/queue/departments` — keep queries cheap (indexes on departmentId, status, checkInAt)

**Verify:** two concurrent call-next requests → exactly one wins, loser gets 409 (test with parallel curl).

### Phase 6 — Settings (3–4 days) — largest single phase

**Objective:** 21 endpoints §6.7.

**Backend (group by sub-resource):**
1. `FacilityController` — `GET/PATCH /settings/facility` (map Hospital→FacilityProfile; **server-side** status transition on HeFRA upload: `active_pending_docs→active`, `suspended→active` — never trust client `status`)
2. `ProfileController` — `GET/PATCH /settings/profile` (HospitalAdmin/StaffMember), `POST /settings/profile/change-password`
3. `OperationalSettingsController` — `GET/PATCH /settings/operational` (queuePriorityLevels, refreshSeconds, slotMinutes, noShowGrace, notificationDefaults)
4. `SessionController` — `GET/DELETE /settings/sessions*` (active session rows; confirm "sign out all keeps current" question §7.4)
5. `TwoFactorController` — `GET/PATCH /settings/2fa` (bool only, D10)
6. `PreferencesController` — `GET/PATCH /settings/preferences`
7. `AccountRequestController` — `GET/POST /settings/account-request` (pending record; 90-day retention copy; **no** auto-deactivate in mock — design the review pipeline §8.2)
8. `InviteController` — `GET/POST /settings/invites`, `DELETE /{id}` (dedupe by email §9.4)
9. `PermissionMatrixController` — `GET/PATCH /settings/permissions` (rows + role/level; **used by @PreAuthorize enforcement**, see Phase 2)
10. `ImageUploadController` — multipart POST → URL (§9.3; reuse `FileStorageService`)

**Slice 1 (DONE — `feat/settings-core`, Aug 18):** items 1, 2, 3, 10 landed.
- `GET/PATCH /api/settings/facility` — maps Hospital (id=facilityId) → FacilityProfile; `region`/`facilityType`/`logoUrl` columns added; status derived server-side (`REJECTED→suspended`, doc→`active`, else `active_pending_docs` + 90-day `hefraDueDate`); HeFRA doc via `hefraDocumentUrl` on PATCH applies `active_pending_docs→active`
- `GET/PATCH /api/settings/profile` + `POST .../change-password` — StaffMember-based; 4 notification-pref booleans added; BCrypt current-password check, ≥8 chars, no-reuse
- `GET/PATCH /api/settings/operational` — one row/facility (`operational_settings`), lazy-created with mock defaults; priority levels validated against emergency|urgent|routine
- `POST /api/uploads/images` — reuses FileStorageService (whitelist + 10MB); `GET /uploads/**` made public so `<img>` tags render (writes stay authenticated)
- Seeder: `ensureSettingsColumns()` (JdbcTemplate ADD COLUMN IF NOT EXISTS — Hibernate ddl-auto=update skipped staff_members columns) + `ensureFacilitySettings()` (region/facilityType + operational row)
- Verified: curl suite (partial PATCH, password cycle, 403 doctor/401 anon, upload, HeFRA transition) + browser E2E on `/d/settings` tabs (Facility/Profile/Operational render live data; HeFRA grace banner shows)
- **Slice 2 remaining:** items 4-9 (sessions, 2fa, preferences, account-request, invites, permissions)

### Phase 7 — Notifications (1–2 days)

**Objective:** §6.6 read/mark endpoints + seed/trigger mechanism (minimal).

**Backend:**
- `model/Notification.java` (id, type, title, body, createdAt, read, link)
- `controller/NotificationController.java` — `GET /notifications`, `/unread-count`, `PATCH /{id}/read`, `POST /read-all` (return full list per FE)
- Trigger rules are unspecified (§10.5) — ship the CRUD surface + seed data; leave trigger wiring as a documented TODO

### Phase 8 — Dashboard & Analytics (2–3 days)

**Objective:** §6.8 stats + §6.9 analytics from real aggregation.

**Backend:**
- `controller/DashboardController.java` — `/dashboard/stats` (live counts: patients-in-queue, avg-wait, appointments-today, no-show-rate + period-over-period trends), `/dashboard/queue` (reuse queue severity logic §7.2), `/dashboard/alerts` (threshold-derived: queue critical, HeFRA deadline), `/dashboard/patient-volume` (hourly bucket), `/facility/current`
- `controller/AnalyticsController.java` — `GET /analytics?from&to` single payload (D7): daily metrics, totals, previous-period comparison, per-department utilization (served/(capacityPerDay×days), clamped 100), **real** `appointmentsByStatus` counts
- Retire mock ratios (§7.7); confirm avgWait definition (D7)

### Phase 9 — Tenant lifecycle & platform plane (3–4 days)

**Objective:** §4 states + §3 platform endpoints.

**Backend:**
- `FacilityAccountStatus` enum: `PENDING, ACTIVE_PENDING_DOCS, ACTIVE, SUSPENDED` (frontend type lacks PENDING — add to FE type §4.2)
- `controller/AccessRequestController.java` — `POST /public/access-requests` (5 fields, rate-limited, creates PENDING)
- `controller/PlatformController.java` — `GET /platform/facilities*` (metadata only §3.3; `<5` count suppression), `PATCH approve/reject/suspend/reactivate`; platform-operator role, separate `aud`, **zero data-layer grants** on facility tables
- `config/ComplianceDeadlineJob.java` — scheduled job: `active_pending_docs` + past `hefraDueDate` + no doc → `suspended`
- `dto/ApprovalTokenService` — real server-verifiable invite token (§4.4/§10.4; share mechanism with staff invite Phase 6)

### Phase 10 — Frontend contract fixes + full mock flip (1 day)

**Objective:** `NEXT_PUBLIC_USE_MOCK=false` on a clean DB; every screen live.

**Frontend changes:**
- `lib/types/settings.ts` — add `"pending"` to `FacilityAccountStatus` (Phase 9 dep)
- `lib/api/access-request.ts` — create swap layer (currently missing §6.10)
- `lib/api/auth.ts` — already created in Phase 1
- Fix clinical-record `medications` bug (Phase 4)
- 401 interceptor (Phase 0)
- Verify all `lib/api/*` swap functions hit real endpoints

**Full QA:** walk every page with mocks off: login/OTP, `/d` (overview, appointments, departments, live-queue, patients, staff, analytics, settings×9 tabs, notifications, profile), `/w` (queue, patients, profile).

---

## 6. Testing & validation strategy

| Layer | Approach |
|---|---|
| Backend unit | Service-layer tests per domain (transition validation, atomic call-next via mock repository, `canDelete` gate) |
| Backend integration | Spring Boot `@SpringBootTest` + Testcontainers/`pulse_db`; endpoint smoke via existing `scripts/` + curl/python (`reset_db.py` pattern exists) |
| Concurrency | Parallel-request test for call-next (two threads, one winner) |
| Frontend | `npm run lint`, `npm run build`; manual QA walk (Phase 10 checklist); Postman collection per domain |
| Contract | Swagger UI per phase; field-by-field diff against spec §5 types |

## 7. Risks & open questions

| Risk | Mitigation |
|---|---|
| **Biggest:** auth-model migration (Doctor/HospitalAdmin → StaffMember) touches every existing endpoint | Phase 1 adds staff auth **alongside** legacy; migrate domains incrementally; don't break mobile patient flow |
| `ddl-auto=update` + new entities = safe; but **renaming/restructuring** existing tables needs manual ALTER (memory: CHECK constraints, NOT NULL) | Plan DB migrations per phase; run `reset_db.py` in dev as needed |
| Frontend repo is `housebuoy/pulse-web` — user doesn't own it | Coordinate FE changes via PR to that repo; keep FE changes minimal & documented (they're isolated to `lib/api/auth.ts`, `lib/axios.ts`, 2 bug fixes, types) |
| Queue atomicity is a hard concurrency requirement | Phase 5 dedicated; atomic UPDATE + row lock; concurrency test before merge |
| Spec §10 open questions (suspended→active UX, billing, multi-facility) block Phase 9 polish, not Phase 1–8 | Flag in each phase's PR description; default to spec's recommended behavior |

## 8. Delivery & commit strategy

- **Backend:** one branch per phase (`feat/facility-auth`, `feat/facility-departments-staff`, …), conventional commits, PRs against `main`, Swagger snapshot regen (`scripts/regen-openapi.sh`) at phase end.
- **Frontend:** PRs to `housebuoy/pulse-web` for the small FE change set (auth swap layer, axios interceptor, 2 bug fixes, types).
- **Docs:** update `BACKEND_SPEC.md`-adjacent docs (or a new `INTEGRATION_STATUS.md`) as phases land — keep the contract doc in sync per the user's doc discipline.

---

## Recommended immediate next step

Phase 0 + Phase 1 (auth vertical slice) — it unblocks everything and proves the pipeline with the smallest change set. Ready to execute when you are; I can run it with subagent-driven-development (fresh subagent per task, two-stage review) or directly task-by-task in this session.
