# Pulse Health — Platform Architecture & Integration Blueprint

> **Audience:** engineers and AI build agents (Grok Build, Hermes, Copilot) working on Pulse.
> **Purpose:** single source of truth for how the **backend**, **web dashboard**, and **mobile app** fit together; which contracts already exist; which endpoints are missing; and ready-to-execute **work packets** that can be offloaded in parallel.
> **Last verified:** 2026-08-18 (backend at `feat/p1-patient-profile`, mobile at `c88d524`, web at latest `main`).

---

## 0. BUILD STATUS & GROK BUILD HANDOFF ⚡ (read this first)

> **To Grok Build (and any agent picking up Pulse work):** this document is the contract. Read §5.2 (mobile data shapes) before defining any DTO, follow §9 conventions, and **never push to `main` or merge** — commit granularly (one file per commit, conventional commits), push a fresh `feat/*` branch, and open a PR. The repo owner (Psam) merges everything himself.
>
> **Also read `PITFALLS.md` (same repo root) — it's a living journal of every trap
> and fix discovered so far (stale-classes build trap, seeder clobbering, web
> build gate failures, mobile lint baseline, git workflow rules). If you hit a NEW
> pitfall, resolve it, then ADD AN ENTRY there for the next agent — including the
> fix and prevention. Both documents are maintained by every agent that works on
> Pulse, Hermes and Grok Build alike.**

### Current progress (2026-08-18)

| Packet | Scope | Status | Where |
|---|---|---|---|
| **P1** | Patient profile + medical profile + vitals | ✅ **DONE** — built, tested, PR open | PR #16 (`feat/p1-patient-profile`) |
| **P2** | Insurance + discovery + availability (mobile shape) | 🔴 next up | §8 P2 |
| **P3** | Book-by-department, reschedule, pay-by-deadline | 🔴 queued | §8 P3 |
| **P4** | Payments: methods, charge, history | 🔴 queued | §8 P4 |
| **P5** | Mobile API client + screen wiring | 🔴 queued (after P1–P4) | §8 P5 |
| **P6** | Web polish (notifications, invites) | ⚪ optional | §8 P6 |

### What P1 delivered (already on PR #16, do not rebuild)
- `GET/PATCH /api/patients/me` — own profile + emergency contact
- `GET/PATCH /api/patients/me/medical` — structured allergies/conditions/medications
- `POST /api/patients/me/vitals` — self-logged vitals, newest-first
- Patient entity: `allergies_json/conditions_json/medications_json/vitals_json` + emergency-contact columns, **kept in sync with legacy flat web fields** (web dashboard contract untouched)
- Seeder: idempotent `ADD COLUMN IF NOT EXISTS` + demo medical profiles; **fixed the demo seeder clobbering clinical fields on every boot**
- All shapes match the mobile stores exactly (§5.2); verified live: 401 anon, 403 staff, reboot persistence

### For the agent picking up P2 next
1. Read §5.2 contracts, then the mobile store file the packet touches (`insurance-store.ts`, `payments-store.ts`, `services/mock/hospital-schedule.ts`).
2. Follow the endpoint specs in §8 P2 exactly — field names must match the mobile interfaces verbatim (no renames, no `{data: ...}` wrappers).
3. Backend build rule: `~/bin/mvnx clean compile` (never plain `mvnw compile` — stale-classes trap). New columns on existing tables MUST also be added in `DataSeeder.ensureSettingsColumns()`.
4. Test like P1 did: boot, patient login, curl each new endpoint (happy + auth-negative), verify reboot persistence, restore DB to seeded state, shut down.
5. Commit granularly → fresh branch `feat/p2-*` → PR → let Psam merge.

---

## 1. System Overview

```
                         ┌─────────────────────────────────────────────┐
                         │              Spring Boot API               │
                         │        psam-717/pulse (backend/demo)       │
                         │  Spring Boot 4.0.4 · Java 25 · PostgreSQL  │
                         │  JWT auth · RBAC (4 roles) · Swagger       │
                         └───────┬──────────────┬──────────────┬──────┘
                                 │              │              │
                    /api (REST, JSON, Bearer JWT)               │
                                 │              │              │
              ┌──────────────────┘              │              └──────────────────┐
              ▼                                 ▼                                 ▼
   ┌───────────────────┐            ┌───────────────────┐            ┌─────────────────────┐
   │   Web Dashboard   │            │   Mobile App      │            │   (future)          │
   │ housebuoy/        │            │ housebuoy/        │            │   WhatsApp /        │
   │   pulse-web       │            │   pulse-mobile    │            │   kiosk / etc.      │
   │ Next.js 16        │            │ Expo 54 · RN 0.81 │            │                     │
   │ /d admin, /w doc  │            │ patient-facing    │            │                     │
   │ TanStack Query    │            │ Zustand stores    │            │                     │
   └───────────────────┘            └───────────────────┘            └─────────────────────┘
```

**Three user types (RBAC):**

| Role | Surface | Auth flow |
|---|---|---|
| `PATIENT` | Mobile app | Phone/Ghana Card + password → JWT (`/api/auth/patient/login`) |
| `HOSPITAL_ADMIN` | Web `/d` | Email + password + OTP (`/api/auth/login` → `/verify-otp`) |
| `DOCTOR` | Web `/w` | Same staff 2FA flow, role claim `DOCTOR` |
| `SUPER_ADMIN` | Web (platform plane) | `superadmin@pulse.gh`, not tied to a hospital |

**Single backend serves every client.** There is no BFF; the mobile app talks to the same `/api` the web dashboard does. Every client is expected to degrade gracefully to mock data when the API is unreachable (web via `NEXT_PUBLIC_USE_MOCK`, mobile via its stores).

---

## 2. Repos & Local Layout

| Repo | Local path | Stack | Plane |
|---|---|---|---|
| `psam-717/pulse` | `D:\Projects\pulse\backend\demo` | Spring Boot 4.0.4, Java 25, PostgreSQL (`pulse_db`), springdoc v3.0.3 | API — all clients |
| `housebuoy/pulse-web` | `D:\Projects\pulse\web-frontend` | Next.js 16, TanStack Query, axios, Tailwind/shadcn | Admin `/d` + Doctor `/w` |
| `housebuoy/pulse-mobile` | `D:\Projects\pulse\pulse-mobile` | Expo 54, RN 0.81.5, expo-router, NativeWind, Zustand | Patient |

**Dev run commands:**

| App | Command | Port |
|---|---|---|
| Backend | `cd backend\demo && .\start.bat` (sets JDK 25) | `:8080` |
| Web | `cd web-frontend && npm run dev` | `:3000` |
| Mobile | `cd pulse-mobile && npm start` (Expo Go) / `npm run android` | Metro `:8081` |

**Backend build trap (read before compiling):** always `~/bin/mvnx clean compile` — incremental `mvnw compile` silently skips edited files and serves stale classes (spurious 401/404s). Verify `BUILD SUCCESS`.

**Demo credentials (seeded):**
- Staff: `sarah.jenkins@knust-hospital.test` / `owusu@pulsehealth.test` / `Password123!` (OTP echoed in backend console, `otp.dev-mode=true`)
- Patients: `PT-00101`–`PT-00110`, password `patient123`
- Super admin: `superadmin@pulse.gh`

---

## 3. Backend — Current State (the contract that exists)

### 3.1 Entities (`model/`)

| Entity | Table | Notes |
|---|---|---|
| `Hospital` | `hospitals` | name, licenseNumber, licenseDocumentUrl, region, facilityType, logoUrl, address, lat/lng, specialties (JSON), capacity, verificationStatus (PENDING/APPROVED/REJECTED), working hours |
| `HospitalAdmin` | `hospital_admins` | fullName, email, password, phone, role |
| `Department` | `departments` | name, abbreviation, description, consultationFee, parent, hospital, status, headDoctorName, rooms, opensAt/closesAt, twentyFourSeven |
| `Doctor` | `doctors` | firstName/lastName, specialization, department, email, phone, licenseNumber, consultationDuration (per-doctor, default 20) |
| `Patient` | `patients` | firstName/lastName, dob, gender, email, phone, ghanaCard, password, address, patientNumber, **bloodType, allergies (string), currentMedications (string), latestVitals (string)** ⚠️ flat strings — see Packet P3 |
| `StaffMember` | `staff_members` | name, role, title, specialty, department, email, phone, shift, dutyStatus, accountStatus, facilityId, notification prefs |
| `TimeSlot` | `time_slots` | doctor, date, startTime, endTime, isBooked |
| `Booking` | `bookings` | patient, doctor (nullable — assigned by hospital later), department, hospital, timeSlot, bookingDate, status, **paymentStatus, amountDue**, checkedIn, checkInTime, priority |
| `QueueEntry` | `queue_entries` | ticketNumber, patientName, departmentId, status (WAITING/CALLED/...), priority, source, checkInAt, calledAt, clinician, room |
| `LoginOtp` | `login_otps` | email, code, expiresAt, attempts, used |
| `PendingRegistration` | `pending_registrations` | fullName, phone, ghanaCard, hashedPassword, otp, expiresAt |
| `OperationalSettings` | `operational_settings` | queuePriorityLevelsJson, queueRefreshSeconds, appointmentSlotMinutes, noShowGraceMinutes, email/SMS prefs |
| `WorkingHours` | `working_hours` | hospital, dayOfWeek (1=Mon..7=Sun), openTime, closeTime, isClosed |

### 3.2 Existing endpoint map (all under `/api`)

| Controller | Path | Methods | Plane |
|---|---|---|---|
| `AuthController` | `/auth/patient` | `POST /signup` (phone OTP), `POST /verify-otp`, `POST /login` (identifier = phone **or** Ghana Card) | mobile |
| `StaffAuthController` | `/auth` | `POST /login`, `POST /login/verify-otp`, `GET /me` | web |
| `AdminController` | `/auth/admin` | `POST /login`, `POST /create-doctor` | web |
| `HospitalController` | `/hospitals` | `GET` (paged), `GET /{hospitalId}`, `POST /register`, `POST /login`, `POST /{hospitalId}/departments`, `DELETE /{hospitalId}/departments/{departmentId}`, `PUT|GET /{hospitalId}/working-hours`, `POST|GET /{hospitalId}/license` | both |
| `DepartmentController` | `/hospitals/{hospitalId}/departments` | `GET` | mobile |
| `FacilityDepartmentController` | `/departments` | `GET` (list), `GET /stats`, `GET /{id}`, `PATCH /{id}`, `PATCH /{id}/head-doctor`, `DELETE /{id}` | web |
| `DoctorController` | `/departments/{departmentId}/doctors` | `GET` | both |
| `TimeSlotController` | `/doctors/{doctorId}/slots` | `GET ?date=` | mobile |
| `DoctorScheduleController` | `/doctors/me/appointments` | `GET` | web |
| `PatientController` | `/patients` | `GET` (list, admin), `GET /{id}`, `POST`, `PATCH /{id}`, `PATCH /{id}/clinical-record`, `POST /{id}/vitals`, `GET /me/bookings` (paged) | both |
| `BookingController` | `/bookings` | `POST` (patientId+timeSlotId), `GET /{id}`, `PATCH /{id}/payment`, `DELETE /{id}` | both |
| `QueueController` | `/queue` | `GET /departments`, `GET /entries`, `POST /call-next`, `PATCH /entries/{id}` | web |
| `AppointmentController` | `/appointments` | `GET /stats`, `GET /departments`, `PATCH /{id}` | web |
| `FacilitySettingsController` | `/settings/facility` | GET/PATCH | web |
| `ProfileSettingsController` | `/settings/profile` | GET/PATCH, `POST /change-password` | web |
| `OperationalSettingsController` | `/settings/operational` | GET/PATCH | web |
| `UploadController` | `/uploads/images` | `POST` (multipart) | both |
| `HospitalAdminController` | `/admin/hospitals/{hospitalId}/verify` | `PUT` | web (super admin) |

### 3.3 Conventions (backend must keep)

- **Errors:** `ApiResponse{status, message, errors[], timestamp}`; `GlobalExceptionHandler` turns unmapped routes into a clean 404 (never a 500 + stack trace). Validation failures return 4xx with guidance messages (never a bare 403/401 — tell the caller what header/field is wrong).
- **Auth:** JWT Bearer in `Authorization` header. Patient JWT carries patient identity; staff JWT carries `hospitalId` claim + role. `@EnableMethodSecurity` + `@PreAuthorize` per endpoint. Swagger/`/v3/api-docs` and scalar paths are permit-all.
- **Pagination:** list endpoints use Spring `Pageable`; clients pass `page`/`size`.
- **Money:** `BigDecimal` for `amountDue` (GHS).
- **Dates:** ISO-8601 (`LocalDate`/`LocalDateTime` → `2026-08-18`, `2026-08-18T14:30:00`).
- **Seeder:** `ensureSettingsColumns()` repairs missing columns on existing tables (Hibernate `ddl-auto=update` never adds them) — any new column on an existing table MUST also be added there.

---

## 4. Web Dashboard — Current State

**What it is:** Next.js 16 app with `/d` (hospital admin) and `/w` (doctor) workspaces. Real 2FA login wired to the backend (OTP from console). Settings-core (facility/profile/operational + image upload) verified end-to-end. 13-check curl suite + browser E2E green.

**Data architecture (the swap pattern the mobile app should copy):**

```
components/hooks → hooks/use-*.ts (TanStack Query) → lib/api/*.ts (swap point) → lib/mock/*.ts | axios → Spring Boot
```

- `lib/api/*.ts` checks `NEXT_PUBLIC_USE_MOCK !== "false"`; when `false`, calls real REST via shared axios (`lib/axios.ts`, `baseURL = NEXT_PUBLIC_API_URL ?? http://localhost:8080/api`, attaches `Authorization: Bearer <pulse_token>` from localStorage, 401-interceptor).
- `lib/mock/*.ts` = in-memory arrays with the exact same shapes as the API responses — **the mock shapes ARE the API contract**.
- `BACKEND_SPEC.md` at the web repo root is the authoritative admin-plane spec (derived from the mocks).

**Known web-plane gaps (documented in BACKEND_SPEC.md §10):** notifications trigger logic, staff invite activation, appointment creation is delegated to patients (bookings), queue check-in POST from web. Not in scope for mobile work below.

---

## 5. Mobile App — Current State & Data Contracts

**What it is:** patient-facing Expo app. 15+ screens, 30+ components, 7 persisted Zustand stores (`AsyncStorage`). **Currently 100% UI shell: zero network calls, zero API client, no auth/session checks** (per `ARCHITECTURE.md` audit 2026-08-14). All screens render hardcoded literals or store seeds.

### 5.1 Screen inventory & what each needs

| Screen | Route | Data it renders (from stores/mock) |
|---|---|---|
| Splash → Login | `/` → `/(auth)/login` | login by **Phone or Ghana Card ID** + password |
| Signup | `/(auth)/signup` | phone + password → OTP screen |
| OTP | `/(auth)/otp` | 6-digit code, resend countdown |
| Onboarding 1 | `/(onboarding)/step1-identity` | first/last name, middle name, DOB (DD/MM/YYYY), email "for receipts" |
| Onboarding 2 | `/(onboarding)/step2-clinical` | blood type, allergies, conditions, medications, NHIS/private insurance + provider search |
| Onboarding 3 | `/(onboarding)/step3-family` | family & emergency contact, shareable family code |
| Home | `/(tabs)/home` | live-queue card (`queue-store`), quick actions, visit history (`records-store`), health-tip banner |
| Queue | `/(tabs)/queue` | `QueueTicket` + Arrived/Cancel/Directions/Reschedule actions |
| Book | `/(tabs)/book-appointment` | hospital cards (name, location, rating, image, distance, wait time), category pills |
| Hospital details | `/(screens)/hospital-details` | `HOSPITAL` literal + `DEPARTMENTS` dropdown + 14-day date strip + grouped time slots (`fetchMockAvailability`) |
| Reschedule | `/(screens)/reschedule` | availability + Confirm Reschedule |
| Records | `/(tabs)/records` | visits, lab results, prescriptions + search/filter (local) |
| Profile | `/(tabs)/profile` | profile header, settings rows, notification toggle (`profile-store`) |
| Payments | `/(screens)/payments` | hero card, outstanding bookings, saved methods, history |
| Health insurance | `/(screens)/health-insurance` | `insurance-store` details + card photo |
| Medical ID | `/(screens)/medical-id` | emergency info, blood group, allergies, vitals |

### 5.2 Mobile data contracts (SOURCE OF TRUTH for new API shapes)

These are the exact TypeScript shapes the app already renders. **Any backend endpoint the mobile app consumes MUST return these shapes (or a superset).** When a packet below defines a DTO, match these field names exactly — no renames, no nested wrappers.

```ts
// queue-store.ts
interface QueueTicket {
  hospitalName: string; department: string; doctorName: string;
  currentNumber: number; userNumber: number; waitTimeMins: number;
  roomNumber: string; estimatedTime: string;   // "10:15 AM"
}

// payments-store.ts
interface OutstandingBooking {
  id: string; facilityName: string; department: string;
  appointmentDate: string /* ISO date */; feeAmount: number /* GHS */;
  payByDeadline: string /* ISO datetime — SERVER ENFORCED, see P4 */;
}
type PaymentNetwork = 'mtn_momo' | 'telecel_cash' | 'card';
interface PaymentMethod {
  id: string; network: PaymentNetwork; label: string;   // "MTN MoMo •••• 4567"
  last4: string; gatewayToken: string;                  // token only, NEVER raw numbers
  isDefault: boolean;
}
interface PaymentHistoryEntry {
  id: string; facilityName: string; department: string;
  methodLabel: string; paidDate: string; amount: number;
}

// records-store.ts
interface Visit {
  id: string; department: string; hospital: string; date: string;
  doctor: string; summary: string;
}
interface LabValue { name: string; value: string; unit?: string; referenceRange?: string; }
interface LabResult {
  id: string; testName: string; hospital: string; orderingDoctor: string;
  date: string; values: LabValue[];
}
interface Prescription {
  id: string; medication: string; dose: string; prescribingDoctor: string;
  hospital: string; date: string;
}

// medical-store.ts
interface AllergyEntry { id: string; label: string; type: 'drug'|'food'|'environmental'; }
interface ConditionEntry { id: string; label: string; }
interface MedicationEntry { id: string; name: string; dose: string; }
interface EmergencyContact { name: string; relationship: string; phone: string; }
interface VitalsEntry {
  id: string; date: string; systolic?: string; diastolic?: string; pulseBpm?: string;
  temperatureC?: string; heightCm?: string; weightKg?: string;
}

// insurance-store.ts
interface InsuranceDetails {
  scheme: string | null; membershipNumber: string; cardholderName: string;
  expiryDate: string | null; cardPhotoUri: string | null;
}

// services/mock/hospital-schedule.ts (availability shape the app already parses!)
interface MockTimeSlot { time: string; available: boolean; }        // "09:00 AM"
interface DaySlots { MORNING: MockTimeSlot[]; AFTERNOON: MockTimeSlot[]; }
interface HospitalAvailability {
  closedDates: string[]; fullDates: string[];
  slots: Record<string, DaySlots>;   // key: 'yyyy-MM-dd'
}
```

**Booking flow state (booking-store.ts):** `facilityName, facilityLocation, department, selectedDate, selectedTime` — the app books **per department** (hospital assigns the doctor later), which matches `Booking.doctor` being nullable.

---

## 6. Integration Contract (how everything talks)

### 6.1 Auth

- **Mobile login:** `POST /api/auth/patient/login` `{identifier, password}` → `AuthResponse{token, role:"PATIENT", userId, message}`. Store token in AsyncStorage; attach `Authorization: Bearer <token>` to every request. (Login accepts phone or Ghana Card — matches the mobile login screen.)
- **Mobile signup:** `POST /api/auth/patient/signup` `{fullName, phone, password, ghanaCard}` → OTP to phone → `POST /verify-otp` `{phone, code}` → then login.
- **Staff (web):** `POST /api/auth/login` → `{token:null, devOtp, session}` → `POST /api/auth/login/verify-otp` → JWT. Not used by mobile.
- **401 handling:** mobile must clear token + route to `/login` on any 401 (mirror web's interceptor).

### 6.2 API base & client

- Base URL: `http://localhost:8080/api` (dev). Use an env var (`EXPO_PUBLIC_API_URL`) so it can be pointed at staging later.
- Every client needs ONE shared HTTP wrapper (fetch or axios) with: base URL, Bearer injection, 401 handler, JSON error parsing into the mobile store's error shape.

### 6.3 Date/time & money rules

- All dates ISO-8601. Mobile displays via `date-fns`.
- Slot times displayed as `"09:00 AM"` — backend returns `LocalTime` (`09:00`) or ISO; **mobile formats**. Do NOT change the mock shape's `time: string` display — backend can return `startTime: "09:00"` and mobile maps it, OR backend returns display string; **decide in P2** (recommendation: backend returns ISO `LocalTime`, mobile formats — single source of truth).

---

## 7. Gap Analysis — backend endpoints MISSING for mobile

Everything the mobile app renders that has no backend counterpart yet:

| # | Mobile need | Backend today | Status |
|---|---|---|---|
| G1 | **Patient profile fetch/update** (`me`) — onboarding identity, medical, family | `GET /patients/{id}` (admin view), `PATCH /{id}`, no self-service `me` profile with medical/family | 🔴 missing |
| G2 | **Structured medical profile** — allergies w/ type, conditions, medications list, emergency contact, vitals history | flat strings `allergies`, `currentMedications`, `latestVitals` + `POST /{id}/vitals` (staff-facing) | 🔴 missing |
| G3 | **Insurance details** (NHIS/private, membership#, cardholder, expiry, card photo) | nothing | 🔴 missing |
| G4 | **Hospital cards for discovery** — name, location, rating, reviews, image, distance, waitTime, open status | `GET /hospitals` returns entity (no rating/reviews/waitTime/image) | 🟡 partial |
| G5 | **Department availability in mobile shape** — 14-day window, `closedDates/fullDates/slots{MORNING,AFTERNOON}` | `GET /doctors/{doctorId}/slots?date=` (doctor-scoped, flat, single date) | 🔴 missing |
| G6 | **Book by department+date+time** (mobile flow) | `POST /bookings` requires `timeSlotId` (doctor-scoped) | 🟡 partial |
| G7 | **My bookings as outstanding payments** w/ `payByDeadline` | `GET /patients/me/bookings` (paged BookingResponse), no deadline field, no payment-method layer | 🟡 partial |
| G8 | **Payment methods CRUD** (tokenized), **pay booking** (gateway charge → confirm), **history** | `PATCH /bookings/{id}/payment` only | 🔴 missing |
| G9 | **My queue ticket** (`QueueTicket` shape incl. wait time, room, estimated time) | `QueueEntry` (no patient linkage), `/queue/*` is staff-plane | 🔴 missing |
| G10 | **Check-in from mobile** (arrive at hospital → queue entry) | nothing patient-facing | 🔴 missing |
| G11 | **Reschedule booking** | only `DELETE /bookings/{id}` | 🔴 missing |
| G12 | **Medical records read** — visits, lab results, prescriptions | nothing | 🔴 missing |
| G13 | **Vitals self-logging** (patient adds BP/weight...) | `POST /{id}/vitals` staff-facing | 🟡 partial |

---

## 8. Work Packets (offload-ready, parallelizable)

Each packet is self-contained: scope → files → contract → rules → verify. **Packets P1–P4 are backend (this repo). P5 is mobile client (pulse-mobile). P6 is web polish.** Packets touch disjoint controllers/repos so they can run in parallel. Order backend packets by dependency: P1 → P2 → P3 → P4 can partially overlap (different controllers), but P2 and P3 both extend `Patient`, so P1 (entity/schema changes) should land first.

### P1 — Patient self-service profile & medical profile (backend) ✅ DONE (PR #16)

> **Status: COMPLETE 2026-08-18.** Delivered on `feat/p1-patient-profile` → PR #16 (awaiting merge). Endpoints live: `GET/PATCH /api/patients/me`, `GET/PATCH /api/patients/me/medical`, `POST /api/patients/me/vitals`. Patient entity has `allergies_json/conditions_json/medications_json/vitals_json` + emergency-contact columns, synced with legacy flat fields. Seeder clobber bug fixed. Spec below kept for reference — do not rebuild.

**Scope:** G1, G2, G13.
**Files:** `model/Patient.java` (add structured medical fields), `seeder` (`ensureSettingsColumns`), `controller/PatientProfileController.java` (new, `/api/patients/me`), `dto/` (PatientProfileResponse, UpdateMedicalProfileRequest, AddVitalsRequest), `service/PatientProfileService.java`, repository additions.

**Schema changes (Patient table):** add `emergency_contact_name`, `emergency_contact_relationship`, `emergency_contact_phone`, `conditions` (TEXT JSON), `medications` (TEXT JSON), `allergies` stays (upgrade to JSON array `[{label, type}]`), `vitals` (TEXT JSON array). Keep existing flat columns for web compat; populate both. **Any new column MUST be added in seeder's `ensureSettingsColumns()`.**

**Endpoints:**
```
GET  /api/patients/me          → PatientProfileResponse          (own profile, patient JWT)
PATCH /api/patients/me         → update identity + emergency contact
GET  /api/patients/me/medical  → MedicalProfileResponse
PATCH /api/patients/me/medical → update allergies/conditions/medications/bloodType
POST /api/patients/me/vitals   → add VitalsEntry (self-logged)
```

**Contracts (match mobile stores exactly):**
```jsonc
// PatientProfileResponse
{
  "id": "PT-00101", "firstName": "Kelvin", "lastName": "Mensah",
  "dateOfBirth": "1998-04-12", "gender": "MALE", "email": "...", "phone": "+233...",
  "ghanaCard": "GHA-...", "address": "...",
  "emergencyContact": { "name": "Ama Quarcoo", "relationship": "Sister", "phone": "+233 20 987 6543" }
}
// MedicalProfileResponse
{
  "bloodGroup": "O+",
  "allergies": [ { "id": "1", "label": "Penicillin", "type": "drug" } ],
  "conditions": [ { "id": "1", "label": "Asthma" } ],
  "medications": [ { "id": "1", "name": "Ventolin Inhaler", "dose": "100mcg, as needed" } ],
  "vitals": [ { "id": "1", "date": "2026-08-18", "systolic": "120", "diastolic": "80",
                "pulseBpm": "72", "temperatureC": "36.8", "heightCm": "178", "weightKg": "74" } ]
}
```
**Rules:** patient JWT only; admin can still read via existing `/patients/{id}`. Vitals are append-only (patient can add, not delete). Seed 3 demo patients with full medical profiles (mirror the mobile store seeds).
**Verify:** `mvnx clean compile` → boot → login as `PT-00101/patient123` → GET/PATCH `/me` and `/me/medical`, POST `/me/vitals`, negative: doctor token → 403.

### P2 — Insurance + discovery + availability (backend)

**Scope:** G3, G4, G5.
**Files:** `model/Patient.java` (insurance fields or `InsuranceDetail` entity), `controller/InsuranceController.java` (`/api/patients/me/insurance`), `controller/MobileDiscoveryController.java` (`/api/mobile/hospitals`, `/api/mobile/hospitals/{id}/departments`, `/api/mobile/departments/{id}/availability`), `dto/` (HospitalCardResponse, DepartmentOptionResponse, AvailabilityResponse), `service/MobileDiscoveryService.java`.

**Endpoints:**
```
GET  /api/patients/me/insurance            → InsuranceDetails (mobile shape, id → cardPhotoUrl)
PUT  /api/patients/me/insurance            → upsert (scheme, membershipNumber, cardholderName, expiryDate)
POST /api/uploads/images                   → reuse; card photo → URL saved into insurance record
GET  /api/mobile/hospitals                 → HospitalCardResponse[] (approved only)
GET  /api/mobile/hospitals/{id}/departments→ DepartmentOptionResponse[] (id, name, consultationFee, description)
GET  /api/mobile/departments/{id}/availability?from=yyyy-MM-dd&days=14
                                           → AvailabilityResponse (mobile shape, see below)
```

**Contracts:**
```jsonc
// HospitalCardResponse (derived from Hospital; rating/reviews/waitTime derived or defaulted)
{
  "id": "1", "name": "KNUST University Hospital", "location": "University Road, Kumasi",
  "rating": 4.8, "reviews": "120+", "image": "/uploads/logo.png", "distanceKm": 2.5,
  "waitTime": "Low", "status": "Open 24/7", "specialties": ["Cardiology","Pediatrics"]
}
// AvailabilityResponse — MUST match fetchMockAvailability output shape
{
  "closedDates": ["2026-08-23"], "fullDates": [],
  "slots": { "2026-08-24": { "MORNING": [ {"time":"09:00 AM","available":true}, ... ],
                             "AFTERNOON": [ {"time":"12:30 PM","available":true}, ... ] } }
}
```
**Rules:** availability is computed from the department's doctors' TimeSlots (slot = doctor.consultationDuration minutes, default 20; morning ≤12:00, afternoon >12:00); closed days come from hospital `working_hours` (Sunday closed); full days = no available slots remain. `Hospital` needs no schema change if rating/reviews default (4.5 / "—") and waitTime derived from queue load; image from `logoUrl`. Only `verificationStatus == APPROVED` hospitals listed.
**Verify:** boot → patient login → GET `/api/mobile/hospitals` (Korle Bu + KNUST seeded approved) → availability for a department returns 14 days, Sundays in `closedDates`, each day's MORNING/AFTERNOON arrays present.

### P3 — Booking by department + reschedule + pay-by-deadline (backend)

**Scope:** G6, G7, G11.
**Files:** `model/Booking.java` (+`payByDeadline`, `feeAmount` already `amountDue`), `model/BookingStatus.java` (add `PENDING_PAYMENT`), `controller/BookingController.java` (extend), `dto/` (MobileBookingRequest, RescheduleRequest, BookingSummaryResponse), `service/BookingService.java` (auto-pick doctor by department rotation; deadline scheduler), seeder.

**Endpoints:**
```
POST /api/bookings/mobile            // body below — department+date+time, no timeSlotId
PATCH /api/bookings/{id}/reschedule  // { newDate, newTime } — validates slot free, moves booking
GET  /api/patients/me/outstanding    // bookings with paymentStatus != PAID, incl. payByDeadline
```

```jsonc
// MobileBookingRequest
{ "departmentId": 3, "date": "2026-08-24", "time": "09:00", "patientId": null /* JWT patient wins */ }
// BookingSummaryResponse (mobile outstanding shape, superset of OutstandingBooking)
{
  "id": "42", "facilityName": "KNUST University Hospital", "department": "General OPD",
  "appointmentDate": "2026-08-24", "feeAmount": 20.00, "payByDeadline": "2026-08-20T09:00:00",
  "status": "PENDING_PAYMENT", "paymentStatus": "UNPAID", "startTime": "09:00"
}
```
**Rules:** booking per department — service picks the department's doctor with fewest bookings that day (or next in rotation); `doctor` field fills in but patient never sees it. `payByDeadline` = booking time + `OperationalSettings.appointmentSlotMinutes`-based window (default 48h); **server-side job** (Spring `@Scheduled`) auto-cancels + releases slot when deadline passes — the mobile store comment explicitly says the server must enforce this. `GET /me/outstanding` returns only unpaid, non-cancelled, not-checked-in bookings.
**Verify:** patient login → POST mobile booking → GET `/me/outstanding` shows it with deadline → reschedule to a free slot → 200; reschedule to a booked slot → 409/400 with guidance message.

### P4 — Payments: methods, charge, history (backend) — GATEWAY: Aza 🔴 in progress (Grok Build, 2026-08-18)

> **Status: IN PROGRESS** — assigned to Grok Build with the Aza gateway. Do not duplicate.

**Scope:** G8.
**Files:** `model/PaymentMethod.java` (new), `model/PaymentTransaction.java` (new), `controller/PaymentController.java` (`/api/patients/me/payment-*`), `dto/` (PaymentMethodResponse, AddPaymentMethodRequest, PayRequest, PaymentHistoryEntryResponse), `service/PaymentService.java`, `seeder`.

**Endpoints:**
```
GET    /api/patients/me/payment-methods    → PaymentMethodResponse[] (mobile shape)
POST   /api/patients/me/payment-methods    // { network, last4, gatewayToken, label } — tokenized only
PATCH  /api/patients/me/payment-methods/{id}/default
DELETE /api/patients/me/payment-methods/{id}
POST   /api/patients/me/payments            // { bookingIds: [42], methodId: 7 } → creates Aza session → checkoutUrl
GET    /api/patients/me/payment-history     → PaymentHistoryEntryResponse[] (mobile shape)
POST   /api/webhooks/aza                     // webhook receiver (permit-all, verified)
```

**Contracts:** mirror `payments-store.ts` shapes exactly (`PaymentMethod`, `PaymentHistoryEntry`).

**Gateway = Aza (aza.systems — the payments API for Ghana):**
- Base URL `https://api.aza.systems`, all endpoints under `/api/v1/merchant/`, auth via `X-Api-Key: ***` header (test keys `aza_test_...` behave like live but move no money). Single environment — no separate sandbox host.
- Checkout flow: `POST /api/v1/merchant/sessions` `{amount, currency}` → hosted link `pay.aza.systems/c/cs_...`; customer pays inside the Aza app (MoMo/card rails are Aza's concern); `checkout.completed` webhook confirms.
- **⚠️ AMOUNT UNIT TRAP:** landing example "₵50.00" → `amount: 5000` implies **pesewas (minor units)**. Must confirm in API explorer before coding; convert exactly once (GHS BigDecimal → minor units).
- **⚠️ NO STORED-INSTRUMENT API:** hosted-checkout only. `PaymentMethod` rows are display metadata (network/label/last4) — never chargeable instruments. `gatewayToken` on the mobile shape = the Aza session id (`cs_...`) stored on the transaction.
- Confirmation ONLY via webhook (never optimistic PAID); idempotent (retries are no-ops). If `AZA_API_KEY` absent → MockGateway fallback (fake `cs_test_mock_...` URL; webhook completes it) so dev needs no key.
- **Never store raw card/MoMo numbers or PINs.** Default-method rule: first added becomes default; deleting default promotes the next.
- Full prompt given to Grok Build includes: endpoints, contracts, unit-conversion rule, mock fallback, idempotency, verification steps (incl. auth negatives + webhook double-delivery).

### P5 — Mobile: API client + screen wiring (pulse-mobile)

**Scope:** G9 (ticket), G10 (check-in) backend too — see note; mobile client for all packets above.
**Files (pulse-mobile):**
```
src/lib/api/client.ts         // fetch/axios wrapper: base URL, Bearer, 401 → /login
src/lib/api/auth.ts           // login, signup, verifyOtp → stores token in AsyncStorage
src/lib/api/patient.ts        // profile, medical, insurance, outstanding, payments
src/lib/api/discovery.ts      // hospitals, departments, availability
src/lib/api/records.ts        // visits, labs, prescriptions
src/lib/api/queue.ts          // my ticket, check-in
src/lib/use-mock.ts           // EXPO_PUBLIC_USE_MOCK-style swap (same pattern as web)
src/hooks/                    // use-profile, use-payments, use-availability (TanStack Query or SWR)
```
Wire stores to hydrate from API on app start (after login): `profile-store` ← `/me`, `queue-store` ← `/queue/me`, `payments-store` ← outstanding+methods+history, `records-store` ← records, `insurance-store` ← insurance. Booking flow: `book-appointment` → real hospital cards; `hospital-details` → real departments + `availability`; proceed → `POST /bookings/mobile` → success → home queue card + payments outstanding.
**G9/G10 backend note:** add `GET /api/queue/me` (patient's live ticket in `QueueTicket` shape — derives `currentNumber` from department queue, `userNumber` from ticket order, `waitTimeMins` from positions × `OperationalSettings.appointmentSlotMinutes`, `estimatedTime` = now + wait) and `POST /api/queue/me/check-in` (marks booking `checkedIn`, creates/links `QueueEntry` with the booking's patient). Both in `QueueController` — can run in P5 in parallel with P4.
**Verify:** `npm ci`, `npx tsc --noEmit` exit 0, eslint 0 errors (99-file prettier baseline unchanged), `npx expo export --platform android` → `Exported: dist`. Manual: patient login → home shows real queue ticket; book a real slot; payments shows real outstanding.

### P6 — Web polish (optional, lower priority)

`BACKEND_SPEC.md` §10 items: notifications triggers, staff invite activation, queue check-in POST. Only after P1–P5.

---

## 9. Conventions Every Builder MUST Follow

1. **Backend:** `~/bin/mvnx clean compile` before declaring success; BUILD SUCCESS required. New columns on existing tables → also in seeder `ensureSettingsColumns()`. Errors via `ApiResponse` with actionable messages. JWT + `@PreAuthorize` on every patient/staff route.
2. **DTO names match the mobile store interfaces.** No `snake_case` on the wire unless the mobile app already uses it. No wrapping DTOs (no `{data: ...}`).
3. **Mobile:** API client is the ONLY place that touches HTTP. Stores stay the single source of UI state; they hydrate from API, never bypass it. Persist the JWT in AsyncStorage under a fixed key (e.g. `pulse_token`) — same key name as web's localStorage so a future shared-auth story is easy.
4. **Dates:** ISO-8601 over the wire; display formatting lives in the client (`date-fns` on mobile).
5. **Money:** numbers (GHS) in JSON; `BigDecimal` server-side.
6. **Mock data = contract.** If you change an API shape, update the mobile store seeds/mocks in the same PR so the app keeps working with the API down.
7. **Testing:** backend curl suite per packet (positive + auth-negative); mobile `tsc --noEmit` + eslint delta + expo export.

---

## 10. Suggested Build Order & Handoff

```
DONE     P1 (profile/medical) ────────────── ✅ merged-state: PR #16 open
NEXT     P2 (insurance/discovery/avail) ────┐
         P3 (booking/reschedule/deadline) ───┼── backend parallel (P1 already landed)
         P4 (payments) ─────────────────────┘
AFTER    P5 (mobile client + queue G9/G10)  ← consumes P1–P4
OPTIONAL P6 (web polish)                    ← optional
```

**Handoff notes for Grok Build:**
- Each packet is a PR-ready unit: entity changes + DTOs + controller + service + seeder + curl verify. Conventional commit (`feat: ...`), push to a branch named `feat/pN-<slug>`.
- The web repo (`BACKEND_SPEC.md`) and mobile repo (`ARCHITECTURE.md` + stores) are the two other sources of truth — read the relevant store file before defining a DTO.
- When in doubt about a shape, **the mobile store interface wins** (§5.2).
- Never merge to `main` directly; stacked-PR retargets have failed before — verify chain after merge (post-merge skill: `pulse-post-merge`).
