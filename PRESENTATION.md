# Pulse Backend — Lecture Prep for Marvinphil

**What it is:** the single Spring Boot REST API behind the Pulse Ghana hospital demo — patients book
appointments and pay through a mobile Expo app, staff run queues/consults through a Next.js web app,
and every screen in both apps calls this backend (live at `https://pulse-o3gj.onrender.com/api`).

**Where it lives:** git repo root `backend/demo` contains a *nested* Maven module `backend/demo/demo`
(`com.example.demo`, artifact `demo`). All Java lives under `demo/src/main/java/com/example/demo/`;
paths below are relative to `D:/Projects/pulse/backend/demo` (module files start with `demo/`).

**How to read this deck:** every claim names a real file — open it and you can answer any audience
question against the code. Companion docs: `README.md` (repo root), `demo/ARCHITECTURE.md`,
`demo/DEPLOY.md`, `demo/PITFALLS.md`, `RBAC-IMPLEMENTATION.md`.

---

## 1. Stack & how to run

| Concern | Reality (verified) |
|---|---|
| Language / framework | Java 25, Spring Boot **4.0.4** parent, Maven — `demo/pom.xml` (`<java.version>25</java.version>`) |
| Web / data / API docs | starter-web, starter-data-jpa, `org.postgresql`, Hibernate `ddl-auto=update`; springdoc 3.0.3 → `/swagger-ui.html`, `/v3/api-docs` — `demo/pom.xml`, `demo/src/main/resources/application.properties` |
| JWT | jjwt 0.12.6 (api/impl/jackson) — `demo/pom.xml` |
| DB defaults | `jdbc:postgresql://localhost:5432/pulse_db`, user `postgres`, password **no default on purpose** (env `DB_PASSWORD`) — `application.properties` |
| Security | **Stateless** Spring Security (`STATELESS`), CSRF off, CORS allow-list, JWT via `JwtAuthFilter` — `config/SecurityConfig.java`, `config/JwtAuthFilter.java` |
| Health / scheduler | `GET /api/status` (`controller/HelloController.java`); `@EnableScheduling` (`DemoApplication.java`); `service/UnpaidBookingExpiryJob.java` every 60 s |

**Compile locally (works — JDK 25 + Maven wrapper):**
`MSYS_NO_PATHCONV=1 cmd.exe /c "...\mvn.cmd -f D:\Projects\pulse\backend\demo\demo\pom.xml compile -DskipTests"`
(`demo/mvnw`, `demo/mvnw.cmd`, `demo/start.bat` exist; `start.bat` auto-locates a JDK 25.)

**Deploy:** Render blueprint `demo/render.yaml` — service `pulse`, runtime docker, repo `psam-717/pulse`,
**branch `main`**, `rootDir: demo`, health `/api/status`, free plan. Secrets: `DB_URL`/`DB_USERNAME`/
`DB_PASSWORD` (sync:false), `JWT_SECRET` auto-generated, `OTP_DEV_MODE=true` ("echoed in login response +
logs … Set to 'false' before real launch"). `demo/Dockerfile`: build `maven:3.9-eclipse-temurin-25` →
runtime `eclipse-temurin:25-jre`, non-root `appuser`, `-Xmx300m`, port `$PORT`. **Render auto-deploys
only pushes to `main`.**

Env knobs (`application.properties`, env-overridable): `JWT_SECRET`, `jwt.expiration` (86400000 ms),
`otp.expiry-minutes=5`, `otp.max-attempts=5`, `otp.dev-mode=${OTP_DEV_MODE:true}`,
`aza.api-key/base-url/webhook-secret`, `arkesel.api-key/sender/sandbox/send-real` (`ARKESEL_REAL`),
`resend.*` (`EMAIL_REAL`), `cors.allowed-origins`.

---

## 2. CODE MAP — package by package (under `demo/src/main/java/com/example/demo/`)

> Teach the pattern: **Controller (thin endpoint layer) → Service (orchestration + rules, `@Transactional`)
> → Repository (Spring Data JPA) → Entity**. Roles are `@PreAuthorize` on controller methods; concerns
> shared across requests live in `config/`.

**`controller/` — ENDPOINT LAYER (~35 REST controllers, one per domain, all `/api/…`):**
`AuthController` `/api/auth/patient` (signup/OTP/login/reset) · `StaffAuthController` `/api/auth`
(login, login/verify-otp, me, password-reset) · `AdminController` `/api/auth/admin` · `BookingController`
`/api/bookings` (mobile create, reschedule + `/reschedule/surcharge`, pay-by-deadline, cancel) ·
`PaymentController` `/api/patients/me` (methods, `POST /payments`, history) · `AzaWebhookController`
`/api/webhooks/aza` (permit-all) · `QueueController` `/api/queue` (`/me`, `/me/check-in`, `/me/cancel`;
staff: `/departments`, `/entries`, `/call-next`, `/entries/{id}`, `/entries/{id}/complete`) ·
`MobileDiscoveryController` `/api/mobile` (hospitals, departments, availability — `hasRole('PATIENT')`) ·
`HospitalController` `/api/hospitals` · `DepartmentController`/`FacilityDepartmentController`/`DoctorController`/
`DoctorScheduleController` (facility-plane CRUD + `GET /api/doctors/me/appointments`) · `StaffController`
`/api/staff` · `TeamAccessController` `/api/settings` (invites, permissions) · `AccountSettingsController`
`/api/settings` (sessions, 2FA, preferences, account-request) · `ProfileSettingsController`
`/api/settings/profile` · `FacilitySettingsController` `/api/settings/facility` · `OperationalSettingsController`
`/api/settings/operational` · `DashboardController` `/api/dashboard` (stats/queue/alerts/patient-volume) ·
`AnalyticsController` `/api/analytics` · patient plane: `PatientController`, `PatientProfileController`
(`/me`), `PatientAppointmentsController` (`/me/appointments`), `PatientOutstandingController`
(`/me/outstanding`), `PatientNotificationController` (`/me/notifications`), `PatientClinicalRecordsController`
(`/api/patients/{patientId}/records`), `InsuranceController` · misc: `NotificationController`,
`TimeSlotController`, `UploadController`, `FacilityController`, `HospitalAdminController`,
`AppointmentController`, `HelloController`. ~90+ `@PreAuthorize` annotations; role strings used:
`ADMIN, DOCTOR, NURSE, FRONT_DESK, READ_ONLY` (staff), `PATIENT` (mobile), `SUPER_ADMIN, HOSPITAL_ADMIN`
(platform). Tenant boundary via `config/SecurityUtils.java` (`requireFacilityId`/`requireStaffId` read
the JWT's `facilityId` claim from Authentication credentials).

**`service/` — ORCHESTRATION/BUSINESS LOGIC (29):** `BookingService` (655-line rules engine:
create/claim/reschedule/cancel/expire), `AuthService`, `StaffAuthService`, `PaymentService`,
`QueueService` + `PatientQueueService`, `PatientRecordsService`, `NotificationService` +
`PatientNotificationService`, `MobileDiscoveryService`, `HospitalService`, `DepartmentService`,
`DoctorAdminService`, `FacilitySettingsService`, `OperationalSettingsService`, `ProfileSettingsService`,
`AccountSettingsService`, `TeamAccessService`, `StaffService`, `AnalyticsService`, `DashboardService`,
`PatientService`, `PatientProfileService`, `AppointmentService`, `InsuranceService`, `ArkeselSmsService`
(external SMS), `ResendEmailService` (external email), `OnlineBookingSupport` (staff↔doctor link helper),
`UnpaidBookingExpiryJob` (`@Scheduled`). Services own transactions and throw domain exceptions
(→ `config/GlobalExceptionHandler`).

**`repository/` — PERSISTENCE (28 Spring Data JPA interfaces):** one per aggregate — `BookingRepository`
(custom JPQL `findOutstandingForPatient`, `findExpiredUnpaid`, `existsByTimeSlotId`,
`existsByPatientIdAndTimeSlotId`), `TimeSlotRepository` (PESSIMISTIC_WRITE queue pick), `QueueEntryRepository`,
`PatientRepository`, `DoctorRepository`, `StaffMemberRepository`, `StaffSessionRepository`, `LoginOtpRepository`,
`PasswordResetOtpRepository`, `StaffPasswordResetOtpRepository`, `PaymentTransactionRepository`,
`PaymentHistoryRepository`, `PaymentMethodRepository`, `HospitalRepository`, `HospitalAdminRepository`,
`DepartmentRepository`, `WorkingHoursRepository`, `OperationalSettingsRepository`, `NotificationRepository`,
`PatientNotificationRepository`, `VisitRecordRepository`, `PrescriptionRecordRepository`,
`LabResultRecordRepository`, `StaffInviteRepository`, `StaffAccountRequestRepository`,
`StaffPreferenceRepository`, `PendingRegistrationRepository`, `PermissionOverrideRepository`.

**`model/` — DATA SHAPE (entities + enums, ~40):** entities `Patient, Booking, TimeSlot, Doctor, StaffMember,
Hospital, HospitalAdmin, Department, WorkingHours, OperationalSettings, QueueEntry, PaymentTransaction,
PaymentMethod, PaymentHistory, Notification, PatientNotification, VisitRecord, PrescriptionRecord,
LabResultRecord, LoginOtp, PasswordResetOtp, StaffPasswordResetOtp, StaffSession, StaffInvite,
StaffAccountRequest, StaffPreference, PendingRegistration, PermissionOverride`. Enums: `StaffRole`
(ADMIN, DOCTOR, NURSE, FRONT_DESK, READ_ONLY), `AdminRole` (PRIMARY_ADMIN, HOSPITAL_ADMIN, STAFF,
SUPER_ADMIN), `BookingStatus` (PENDING_PAYMENT, CONFIRMED, CANCELLED), `PaymentStatus` (PENDING, PAID,
FAILED, REFUNDED), `PaymentTxnStatus`, `QueueStatus` (WAITING, IN_CONSULTATION, COMPLETED, NO_SHOW,
SKIPPED, CANCELLED), `QueuePriority`, `PatientSource`, `VerificationStatus`, `Gender`. ⚠ Two role
vocabularies (facility `StaffRole` vs platform `AdminRole`); `Booking` also keeps a legacy free-text
`appointmentStatus` ("scheduled/confirmed/checked_in/completed/cancelled/no_show") that the
appointment/queue plane treats as authoritative (see `AppointmentService`).

**`dto/` — DATA SHAPE (records, ~100):** flat request/response records (`LoginRequest`, `BookingRequest`,
`RescheduleRequest`, `CompleteConsultRequest`, `CheckoutResponse`, `SurchargeRequiredResponse`,
`ApiResponse`, …) + subpackages `dto/analytics/`, `dto/dashboard/`, `dto/settings/`.

**`payment/` — EXTERNAL INTEGRATION:** `PaymentGateway` (interface) → `AzaPaymentGateway` (real,
`aza.base-url` default `https://api.aza.systems`) / `MockPaymentGateway` (used when `AZA_API_KEY` unset),
selected by `PaymentGatewayConfig`; `CheckoutSession` (checkoutUrl + sessionId), `AzaAmountConverter`
(GHS major⇄minor, BE-5 note). Services code to the interface — real/mock is config, not code.

**`config/` — CROSS-CUTTING:** `SecurityConfig`, `JwtAuthFilter`, `JwtUtil`, `SecurityUtils`,
`DataSeeder` (`CommandLineRunner`, fixtures + self-healing SQL), `GlobalExceptionHandler`
(`@RestControllerAdvice`), `FileStorageService`, `WebConfig`, `OpenApiConfig`.

**`exception/` + `util/` + root:** `ConflictException` (409), `ResourceNotFoundException` (404),
`SurchargeRequiredException` (carries `surchargeAmount` → 402); `GhanaPhoneValidator`;
`DemoApplication.java` (`@SpringBootApplication @EnableScheduling`).

---

## 3. FEATURE MAP — with real code pointers

**3.1 Staff login + email OTP 2FA** — `StaffAuthController` → `StaffAuthService.login`: if
`!staff.isTwoFactorEnabled()` → **straight JWT** (L90-97, token carries `sid`); else delete old codes,
save one `LoginOtp(email, 6-digit code, expiresAt = now + otp.expiry-minutes)` (L99-104), echo as
`devOtp` when `otp.dev-mode` (L113). `verifyLoginOtp` deliberately **not `@Transactional`** so failed-attempt
counters persist; 5 attempts → used (L119-149); delivery is TODO (code logged). Session model
`StaffSession` + `StaffSessionRepository`: `JwtAuthFilter` rejects any token whose `sid` row was deleted
→ real remote sign-out. Reset: `StaffPasswordResetOtp` + `ResendEmailService` (`EMAIL_REAL=false` →
dev echo). Files: `controller/StaffAuthController.java`, `service/StaffAuthService.java`,
`model/LoginOtp.java`, `model/StaffSession.java`, `config/JwtAuthFilter.java`, `service/ResendEmailService.java`.

**3.2 Patient auth — signup/OTP/reset + Arkesel SMS** — `AuthController` → `AuthService`: signup writes
`PendingRegistration`, SMS OTP; `verifyOtpAndCreatePatient` issues public `patientNumber` (PT-…, L121).
Login takes **one identifier** resolved phone → ghanaCard → patientNumber
(`findByPhone.or(findByGhanaCard).or(findByPatientNumber)`, L132-137); unknown identifiers still run a
BCrypt compare against `dummyPasswordHash` (anti-enumeration, L44/L140). Forgot-password responds
**identically whether or not the account exists** (L165-191). Delivery: `ArkeselSmsService` (sandbox
flag; real SMS only with key + sender **and** `ARKESEL_REAL=true`, else dev-logged). Files:
`service/AuthService.java`, `service/ArkeselSmsService.java`, `model/PasswordResetOtp.java`,
`model/PendingRegistration.java`.

**3.3 RBAC + invites/request-access** — `StaffRole` (5 facility roles) + `@PreAuthorize` per method
(queue reads = 5 roles, writes drop READ_ONLY, `complete` = ADMIN|DOCTOR|NURSE; mobile =
`hasRole('PATIENT')`; constants `READ_ROLES`/`WRITE_ROLES` in some controllers). `SecurityConfig` gives
URL-level `authenticated()` + JSON 401/403; method-level annotations are the real gate. Invites:
`StaffController` (`/api/staff`) + `model/StaffInvite.java`; team: `TeamAccessController`
(`/api/settings/invites`, `/permissions`) → `TeamAccessService`; request-access:
`AccountSettingsController` account-request + `model/StaffAccountRequest.java`; platform admins
(`AdminRole.SUPER_ADMIN/HOSPITAL_ADMIN`) via `AdminController`/`HospitalService` (issues those role
strings at login — `HospitalService.java` L94/L112).

**3.4 Hospital/department/facility admin + working hours + doctors** —
`HospitalController`/`HospitalService` (register/login, license upload via `FileStorageService`,
working-hours GET/PUT, `WorkingHoursRepository`); `DepartmentController`/`FacilityDepartmentController`
→ `DepartmentService`; `FacilitySettingsController` → `FacilitySettingsService`; `OperationalSettingsController`
→ `OperationalSettingsService` (+ `payByDeadlineHours` used by booking deadlines);
`DoctorController`/`DoctorScheduleController` → `DoctorAdminService`. Facility staff live in
`StaffMember`/`staff_members`, **separate from legacy `Doctor` rows** (§5).

**3.5 Mobile discovery** — `MobileDiscoveryController` → `MobileDiscoveryService`: only
`VerificationStatus.APPROVED` hospitals (`requireApprovedHospital`); availability window default 14 days
(max 31); slot grid from dept `opensAt/closesAt` or `WorkingHours` (no hours → Sunday closed); occupancy
per date+start-time over existing `TimeSlot` rows: `isAvailable = bookedCount < doctorCount` (L199-205);
returns morning/afternoon `DaySlots` + `closedDates` + `fullDates` (`AvailabilityResponse`).

**3.6 Booking + slot claim + duplicate protection** — `BookingController` (`POST /api/bookings/mobile`) →
`BookingService.createMobileBooking`: status PENDING_PAYMENT, payment PENDING, pay-by deadline (default
48 h via `deadlineHoursFor`); CONFIRMED only after webhook payment. `claimSlot` (L501-519) reuses an
unbooked `TimeSlot` row **only if no booking references it** — `existsByTimeSlotId` (cancelled bookings
keep their OneToOne-unique slot row) — else creates a fresh row with `booked=true`; `assertSlotBookable`
re-validates availability before writes; `pickDoctor` = least-loaded staff-linked doctor. Patient-side
dup guard `existsByPatientIdAndTimeSlotId`.

**3.7 Reschedule — earlier-date GH₵20 surcharge (402)** — `BookingService.reschedule` (L326-390):
compute `earlier`; 409 if IN_CONSULTATION; if `earlier && rescheduleSurchargePaidAt == null` throw
`SurchargeRequiredException` **before any slot work** → `GlobalExceptionHandler` returns **HTTP 402**
`{code: "EARLIER_RESCHEDULE_SURCHARGE_REQUIRED", surchargeAmount: 20.00}` (`SurchargeRequiredResponse`).
Pay via `POST /api/bookings/{id}/reschedule/surcharge` → `PaymentService.startSurchargeCheckout`
(constant `RESCHEDULE_SURCHARGE = 20.00` L56; `PaymentTransaction.kind = "RESCHEDULE_SURCHARGE"` L274;
amount via `AzaAmountConverter`). Webhook → `completeSession` **stamps `Booking.rescheduleSurchargePaidAt`
instead of flipping payment status** (L307-321; `model/Booking.java` L70); the completed reschedule
clears the stamp so the next earlier move bills again (L373-377). Contract spelled out in the doc comment
L309-324.

**3.8 Cancel + unpaid-pending leak fix** — patient: `DELETE /api/bookings/{id}` →
`BookingService.cancelBooking`; staff: `AppointmentService` transition to `cancelled` mirrors
`cancelBooking` + frees slot (L161-175). The fix is the JPQL in
**`BookingRepository.findOutstandingForPatient`**: outstanding = unpaid ∧ not CANCELLED ∧
`(b.appointmentStatus IS NULL OR b.appointmentStatus <> 'cancelled')` ∧ not checked-in (L45-54) — a
cancelled unpaid pending booking no longer appears in the mobile pay-now list.

**3.9 Aza hosted checkout + webhook** — `PaymentController` (`POST /api/patients/me/payments`) →
`PaymentService.startCheckout`: validates method ownership + bookings not cancelled/paid/checked-in,
total > 0, `paymentGateway.createSession(amountMinor, "GHS")`, persists `PaymentTransaction`
(`provider="aza"`, `kind="BOOKING_FEE"`, PENDING, `bookingIds`), returns `CheckoutResponse(checkoutUrl,
sessionId)`. `AzaWebhookController` (`POST /api/webhooks/aza`, permit-all) verifies optional
`X-Aza-Signature`/`X-Webhook-Secret` vs `aza.webhook-secret`, extracts `sessionId`, ignores non-checkout
events, calls `PaymentService.completeSession(sessionId)` — **idempotent**: already-COMPLETED txn →
"Ignoring duplicate Aza webhook", false (L296-300); else flips each booking PAID + CONFIRMED (or stamps
surcharge paidAt), writes `PaymentHistory`, txn COMPLETED.

**3.10 Queue: check-in, tickets, call-next, skip/no-show, complete-consult** — patient
`/api/queue/me/*` → `PatientQueueService` (check-in on active booking; ticket numbers e.g. `C-001` —
prefix from dept abbreviation, `maxTicketSequenceForPrefix+1` in `createWalkIn`). Staff `QueueService`:
`callNext` (by entryId or oldest WAITING by checkInAt asc) WAITING→IN_CONSULTATION + `calledAt`/
clinician; patient "you're next" ping + staff fan-out both best-effort try/catch (L148-176). **Race
protection:** repository picker locks the row PESSIMISTIC_WRITE — concurrent call-next loses with a 409
(L122-135). `updateStatus` = explicit state machine (L231-244): WAITING→IN_CONSULTATION|NO_SHOW|SKIPPED|
CANCELLED; IN_CONSULTATION→COMPLETED|NO_SHOW; terminal states locked. **Complete-consultation**
(`POST /api/queue/entries/{id}/complete`, needs `CompleteConsultRequest`): IN_CONSULTATION→COMPLETED,
linked booking `appointmentStatus="completed"`, then inside the **same `@Transactional`** writes
`PatientRecordsService.addVisit` + one `addPrescription` per item + patient ping (L253-315). Files:
`controller/QueueController.java`, `service/QueueService.java`, `service/PatientQueueService.java`,
`model/QueueEntry.java`, `model/QueueStatus.java`.

**3.11 Patient records** — `PatientClinicalRecordsController` (`/api/patients/{patientId}/records` GET;
POST `/visits`, `/prescriptions`; role-gated) → `PatientRecordsService`; entities `VisitRecord`,
`PrescriptionRecord`, `LabResultRecord` + repos; vitals/update DTOs exist; DataSeeder seeds demo records
for PT-00101 (L523-566).

**3.12 Notifications fan-out** — staff: `NotificationController` (`/api/notifications`) →
`NotificationService` (`create`, `notifyQueueStaff` = one `Notification` per FRONT_DESK/ADMIN at the
facility — L44-54, top-100 list, unread, mark-read). Patient: `PatientNotificationController`
(`/api/patients/me/notifications`) → `PatientNotificationService` (same shape). Emitters:
`BookingService.notify*`, `QueueService`, `AppointmentService` — all best-effort.

**3.13 Dashboard & analytics** — `DashboardController` (`/api/dashboard/stats|queue|alerts|patient-volume`)
→ `DashboardService` + `dto/dashboard/*`; `AnalyticsController` (`/api/analytics`) → `AnalyticsService`
+ `dto/analytics/*` (totals, status breakdown, daily metrics, facility/department).

**3.14 Settings / 2FA gating / profile** — `AccountSettingsController` (`/api/settings`): `/sessions` +
sign-out (revocation enforced in `JwtAuthFilter`), `GET/PATCH /2fa` — the toggle **really gates login**
(`StaffAuthService.login` L90 checks `two_factor_enabled`), `/preferences`, `/account-request`.
`ProfileSettingsController` (`/api/settings/profile`, change-password) → `ProfileSettingsService`.
Backed by `dto/settings/*` and DataSeeder's `ensureSettingsColumns`.

**3.15 Scheduler + demo fixtures** — `UnpaidBookingExpiryJob` (`@Scheduled(fixedRate=60_000)`) →
`BookingService.expireOverdueUnpaidBookings`: unpaid past `payByDeadline` (`findExpiredUnpaid`) →
release slot (`booked=false`) → CANCELLED; server-side only ("a closed or clock-wrong client cannot be
trusted"). `DataSeeder` on every boot: `ensureConstraintRepair` drops stale CHECK constraints blocking new
enum values; `ensureSettingsColumns` runs idempotent `ADD COLUMN IF NOT EXISTS` (staff prefs +
`two_factor_enabled`, patient medical/insurance JSON, pay-by); then seeds Korle Bu + Ridge (+KNUST
University Hospital, APPROVED), departments (Cardio 350, Ortho 400, Peds 250, Neuro 500 GH₵, KNUST GOPD
20), doctors (password `admin123`), facility staff (`@pulsehealth.test`, `Password123!`), super admin
`superadmin@pulse.gh / superadmin123`, patients Ama/Kofi/Efua (+233 24 000 0001…, PT-…), slots (3 days,
08:00-17:00, 20 min), bookings/queue/payment methods/medical data — all `ensure*` methods idempotent by
existence check. **Self-heal:** `ensureLegacyDoctorOwusu` (Doctor row by staff email),
`refreshStaleDemoQueueTimestamps` (re-stamps WAITING/IN_CONSULTATION rows older than 2 h, order kept),
`backfillQueueClinicianIds` (clinicianId by name→email).

---

## 4. DISCUSSION QUESTIONS (ask against the code)

**Q1. Where is RBAC enforced — URL config or annotations?** Both, layered. `SecurityConfig.filterChain`
gates whole path groups (`authenticated()`, selective `permitAll`) and registers JSON 401/403 handlers;
fine-grained roles are `@PreAuthorize` per controller method via `@EnableMethodSecurity`.
`config/SecurityConfig.java`, any controller (e.g. `QueueController`, `MobileDiscoveryController`).

**Q2. How does a JWT become a principal?** `JwtAuthFilter` (OncePerRequestFilter before
`UsernamePasswordAuthenticationFilter`): parses claims (`sub`=id, `role`, `sid`, `facilityId`), rejects
tokens whose `sid` session row was deleted, builds `UsernamePasswordAuthenticationToken(userId,
facilityId, [ROLE_<role>])`; parse failure clears context → JSON 401 entry point. `config/JwtAuthFilter.java`,
`config/SecurityConfig.java` L94/L116-150.

**Q3. OTP lifecycle — storage, expiry, brute-force?** `LoginOtp` stores 6-digit code, `expiresAt = now+5min`
(`otp.expiry-minutes`), `attempts`, `used`; one active code per account (`deleteByEmail` first); verify
caps at `otp.max-attempts=5` then marks used; verify method is deliberately not `@Transactional` so
attempt counters survive the exception. `model/LoginOtp.java`, `service/StaffAuthService.java` L99-159.

**Q4. What stops two claims on one slot?** `claimSlot` treats `booked=true` rows as taken and never
reuses a row still referenced by a booking (`existsByTimeSlotId`) — creates a fresh row instead;
`assertSlotBookable` re-checks before writes; `existsByPatientIdAndTimeSlotId` guards duplicates.
Queue concurrency uses a PESSIMISTIC_WRITE lock; slot/booking uniqueness relies on these existence
checks + OneToOne uniqueness. `service/BookingService.java` L440-519, `repository/BookingRepository.java`.

**Q5. Trace the GH₵20 earlier-move surcharge end to end.** `reschedule` detects `earlier` and throws 402
(before slot work) when `rescheduleSurchargePaidAt == null` → client pays `POST
/api/bookings/{id}/reschedule/surcharge` (`PaymentService.startSurchargeCheckout`, kind
`RESCHEDULE_SURCHARGE`, amount 20.00) → Aza hosted page → webhook `completeSession` stamps
`Booking.rescheduleSurchargePaidAt` + writes `PaymentHistory` → reschedule retry succeeds and clears the
stamp so the next earlier move bills again. `service/BookingService.java` L309-390,
`service/PaymentService.java` L56-59/L237-343, `config/GlobalExceptionHandler.java` L118-125,
`model/Booking.java` L70.

**Q6. Webhook is permit-all — safe? idempotent?** Aza can't send our JWT, so it's permitted; the
controller checks an optional signature header vs `aza.webhook-secret` and requires the sessionId;
`completeSession` looks up the txn **by session id**, errors on unknown, and logs+skips already-COMPLETED
txns — duplicate webhooks can't double-mark. `controller/AzaWebhookController.java`,
`service/PaymentService.java` L289-343, `config/SecurityConfig.java` L82.

**Q7. Queue state machine — which transitions are illegal?** WAITING→IN_CONSULTATION|NO_SHOW|SKIPPED|
CANCELLED; IN_CONSULTATION→COMPLETED|NO_SHOW; CANCELLED and completed/no_show/skipped are terminal.
Illegal moves throw 409 via a switch in `updateStatus`. `model/QueueStatus.java`,
`service/QueueService.java` L224-251.

**Q8. Complete-consult writes ticket + booking + records — one transaction?** Yes: the whole
`QueueService.completeConsultation` is `@Transactional` (ticket→COMPLETED, booking `appointmentStatus`,
`addVisit`, per-item `addPrescription`, patient ping); a mid-way failure rolls everything back. The ping
is try/catch best-effort inside it. `service/QueueService.java` L253-315.

**Q9. How does "now serving" fan out, and can it break call-next?** Patient ping +
`NotificationService.notifyQueueStaff(facilityId,…)` = one `Notification` per FRONT_DESK/ADMIN at the
facility; both wrapped in try/catch ("never fails call-next"); bad department ids are swallowed.
`service/QueueService.java` L148-176, `service/NotificationService.java` L44-54.

**Q10. Scheduler risks?** `@Scheduled` in-memory single-instance every 60 s; expiry is idempotent
(JPQL excludes already-CANCELLED) so duplicate runs/restart overlap are harmless; deadline can slip by
one interval + downtime, mitigated by lazy expiry inside `listOutstanding`. `service/UnpaidBookingExpiryJob.java`,
`service/BookingService.java` L392-415.

**Q11. `Doctor` vs `StaffMember` — same table?** No. Legacy `Doctor` rows (mobile bookings) and
`staff_members` (facility roster) are **linked by email**, not id (`OnlineBookingSupport.staffLinkedDoctors`);
`DataSeeder.ensureLegacyDoctorOwusu` creates a Doctor row for staff "Dr. Owusu" because bookings attach
to legacy doctors and the web filters by doctor name. Type drift too: `Doctor.workspaceId` String,
`StaffMember.departmentId` String, but `Department.id`/`facilityId` Long; queue code converts with
`Long.valueOf`. `model/Doctor.java` L36, `model/StaffMember.java` L38, `service/OnlineBookingSupport.java`,
`config/DataSeeder.java`, `service/QueueService.java` L186.

**Q12. `ddl-auto=update` on a live DB — how does the app stay healthy?** DataSeeder repairs what Hibernate
won't: drops stale CHECK constraints that reject new enum values and runs idempotent `ADD COLUMN IF NOT
EXISTS`, then re-runs `ensure*` fixtures guarded by existence checks (`existsByEmail`, `countByTicketNumber`,
`existsByPatientIdAndTimeSlotId`) — safe to run on every boot. `config/DataSeeder.java` L120-140/L253-330/L762-803.

**Q13. How do the JSON error bodies and 402 work?** `GlobalExceptionHandler` (`@RestControllerAdvice`):
validation→400 field list; bad JSON/type→400; not-found→404; `ConflictException`→409; `SurchargeRequiredException`→**402**
`{code, surchargeAmount}`; auth→401; access denied→403; data-integrity→409 (duplicate/FK heuristics);
unknown route→404 "Endpoint not found"; catch-all→500. Body helper `ApiResponse.error`.
`config/GlobalExceptionHandler.java`, `exception/*.java`, `dto/ApiResponse.java`.

**Q14. CORS — who may call from a browser?** `cors.allowed-origins` (default localhost:3000/8081, the
deployed Vercel web apps, pulselabs.tech), standard methods, exposes `Authorization`; mobile apps send no
Origin so unaffected. `config/SecurityConfig.java` L99-113, `application.properties`.

**Q15. Phone | GhanaCard | PT-code login — show the query.** `AuthService.patientLogin` chains optionals
`findByPhone(identifier).or(findByGhanaCard).or(findByPatientNumber)`; unknown identifiers still pay a
BCrypt compare against a dummy hash for uniform failures. `service/AuthService.java` L131-156,
`repository/PatientRepository.java`.

**Q16. Why can mobile availability disagree with `assertSlotBookable`?** Availability is a lock-free
snapshot heuristic — `isAvailable` frees a time while `booked < doctorCount` (some doctor free, not which
one). Booking is per-doctor: `pickDoctor` picks the least-loaded doctor whose row is unbooked, then
`claimSlot` flips/creates the row; in between another patient can take that doctor, a cancelled booking
can still reference a row (no reuse), so the 409 at booking time is the authoritative re-check.
`service/MobileDiscoveryService.java` L97-205, `service/BookingService.java` L440-519.

**Q17. Who can flip a booking to PAID?** Only the webhook path normally: `completeSession` sets PAID +
CONFIRMED after Aza reports completion; `startCheckout` refuses cancelled/paid/checked-in bookings;
`PATCH /api/bookings/{id}/payment` (`BookingService.updatePaymentStatus`) is the staff/demo override.
`service/PaymentService.java`, `controller/BookingController.java` L110.

**Q18. Does the 2FA toggle in Settings change login behaviour?** Yes — `StaffAuthService.login` checks
`isTwoFactorEnabled()`: false → JWT immediately; true → OTP step. Toggle writes `two_factor_enabled` via
`AccountSettingsController PATCH /api/settings/2fa` → `AccountSettingsService.setTwoFactor`.
`service/StaffAuthService.java` L85-113, `controller/AccountSettingsController.java` L69-79.

**Q19. Cancel: what happens to the slot and the queue ticket?** Booking/Appointment cancel frees the
slot (`booked=false`) and the JPQL filter drops the booking from payable-outstanding even when never
paid (the `appointmentStatus <> 'cancelled'` fix); a live WAITING ticket is cancelled explicitly on
reschedule/complete paths. `service/BookingService.java` L359-390/L400-415, `repository/BookingRepository.java` L45-54.

**Q20. Staff and patient OTPs — shared code?** No, deliberately separate: `LoginOtp` + sessions for staff;
`PasswordResetOtp`/`PendingRegistration` + SMS for patients; staff resets use `StaffPasswordResetOtp` +
`ResendEmailService`. `model/LoginOtp.java`, `model/PasswordResetOtp.java`,
`model/StaffPasswordResetOtp.java`.

**Q21. Where does demo data come from on a fresh DB — why no duplicates on restart?** `DataSeeder.run`:
if hospitals exist it runs only the idempotent `ensure*` pass; otherwise it bulk-seeds the full fixture
set; every ensure is guarded by existence checks. `config/DataSeeder.java` L120-140 + ensure* methods.

**Q22. Cedis or minor units — where does the unit bug live?** `Booking.amountDue` is GHS major (dept fee);
`AzaAmountConverter.toAzaAmount(total)` converts for Aza and `PaymentTransaction.amountMinor` stores "as
sent to Aza (their amount field is GHS major units — see AzaAmountConverter; bug-triage BE-5)". Two
amount fields with different units = a real triaged bug. `service/PaymentService.java` L200-225,
`payment/AzaAmountConverter.java`, `model/PaymentTransaction.java`.

**Q23. `/{patientId}/records` vs `/patients/me/...` — who may read clinical records?** `/me/*` = patient
JWT self-service; records = staff endpoints at `/api/patients/{patientId}/records` gated
ADMIN|DOCTOR|NURSE|FRONT_DESK (reads) / minus READ_ONLY (writes). `controller/PatientClinicalRecordsController.java`
L40-85, `controller/PatientProfileController.java`.

**Q24. Two staff click call-next simultaneously?** The picker locks the candidate with PESSIMISTIC_WRITE;
the loser's locked re-read sees non-WAITING and gets a 409 — never a double serve. No entryId → oldest
WAITING by checkInAt. `service/QueueService.java` L120-135, `repository/QueueEntryRepository.java`.

**Q25. Add a read-only dashboard role — what must change?** `READ_ONLY` already exists and is excluded
from write `@PreAuthorize` sets (see `READ_ROLES`/`WRITE_ROLES` constants); to add a role: extend
`StaffRole`, grant in `@PreAuthorize` strings, seed in `DataSeeder.ensureFacilityDemoData` — no JWT
change needed (filter copies the `role` claim set at login). `model/StaffRole.java`,
`config/SecurityConfig.java`, `service/StaffAuthService.java` L92-93/L154-155.

---

## 5. Gotchas worth telling the audience

- **Two staff-ish tables:** legacy `Doctor` rows and `staff_members` are separate, linked **by email**
  not id; the web workspace even filters appointments by doctor *name* (`DataSeeder.ensureLegacyDoctorOwusu`
  comment). Ask about a doctor → check both tables.
- **ID type drift:** `Doctor.workspaceId` and `StaffMember.departmentId` are `String`; `Department.id`/
  `facilityId` and booking ids are `Long`; `QueueEntry.departmentId` is String too — don't assume Long.
- **READ-ONLY Render Postgres MCP** is used to debug the live DB — look, don't write; and the schema is
  partly `ddl-auto=update`-evolved, so some columns exist that no entity declares.
- **Render deploys only `main`** (blueprint `branch: main`, `rootDir: demo`) — unmerged branches never
  reach the live API; the free instance sleeps, so the first request after idle is a slow cold start.
- **Seeded demo accounts** (`DataSeeder`): doctors & `superadmin@pulse.gh` → `admin123`; super admin
  `superadmin@pulse.gh / superadmin123` (ensureSuperAdminExists even resets a changed password back);
  facility staff `@pulsehealth.test` → `Password123!`; demo patients Ama/Kofi/Efua on
  `+233 24 000 0001…` with `PT-` numbers; KNUST General OPD fee GH₵20.
- **SMS/email sandboxed by default:** real SMS only when `ARKESEL_REAL=true` + key + sender; email only
  when `EMAIL_REAL=true`; and `OTP_DEV_MODE=true` on Render echoes codes in responses/logs — staff 2FA
  email delivery is still a TODO (codes are logged).
- **Payments in demo:** with `AZA_API_KEY` unset, `PaymentGatewayConfig` wires `MockPaymentGateway` —
  the hosted checkout is simulated, but the full session/webhook path still runs and flips bookings to PAID.
- **No test fakes:** verification is live E2E against the deployed API (`test_rbac.py` at repo root);
  this is a deployed web API, not a PyPI package — twine/publish doesn't apply.
- **`/api/auth/admin/**` is URL-permitAll** in `SecurityConfig` — like every login endpoint, credential
  checks happen in code (`AdminController`/`HospitalService`), not in the filter chain.
- **There is no `demo/README.md`** — the runbooks are repo-root `README.md` plus `demo/ARCHITECTURE.md`,
  `demo/DEPLOY.md`, `demo/PITFALLS.md`; don't point the audience at a file that doesn't exist.
