# Pulse — Pitfalls & Resolutions (Living Document)

> **What this is:** a shared journal of every trap, bug, and "how we fixed it" that
> agents (Hermes, Grok Build, Copilot) hit while working on Pulse.
> **Why it exists:** so no agent wastes hours rediscovering what another already
> solved. When YOU hit a pitfall, resolve it, then **add an entry here** — the next
> agent (and the one after) will read it.
> **Audience rule:** this file lives in the backend repo because that's where most
> build work happens, but it covers the whole stack (backend, web, mobile, git).
> **Workflow rule (read before touching git):** never push to `main`, never merge —
> commit granularly (one file per commit, conventional commits), push a fresh
> `feat/*` or `fix/*` or `docs/*` branch, open a PR. The repo owner merges.

---

## How to add an entry (do this every time you solve something)

Copy this template to the right section, fill it in, keep it tight:

```markdown
### <One-line title — symptom first>
- **When:** <date or approx>
- **Symptom:** <what broke / what confused you>
- **Root cause:** <the actual why>
- **Fix:** <exact commands / code change that resolved it>
- **Prevention:** <what to check before hitting it again>
```

Rules:
- One entry per pitfall. Update an existing entry if it's the same root cause.
- Commands must be copy-pasteable from git-bash on Windows (the dev machine).
- If a fix is already documented, don't re-add it — reference the entry number.

---

## 1. Backend — build & compile

### 1.1 `mvnx clean compile` is non-negotiable (stale-classes trap)
- **When:** Aug 2026, recurring.
- **Symptom:** you edit a Java file, `mvnw compile` says SUCCESS, but the server
  serves OLD behavior — spurious 401 on `/api/patients`, 404s on endpoints you
  just wrote.
- **Root cause:** Maven incremental compilation silently skips edited files when
  timestamps/state look unchanged; the running JVM then serves stale classes.
- **Fix:** always build with `~/bin/mvnx clean compile` and verify `BUILD SUCCESS`.
  Never plain `mvnw compile`.
- **Prevention:** make `mvnx clean compile` the first command of any backend change.

### 1.2 Plain `./mvnw spring-boot:run` fails from git-bash (classworlds)
- **When:** Aug 18 2026.
- **Symptom:** `./mvnw spring-boot:run` → `Error: Could not find or load main class
  org.codehaus.plexus.classworlds.launcher.Launcher`.
- **Root cause:** the stock `mvnw` shell script passes MSYS-mangled paths
  (`/c/...`) to the Windows JVM, which can't read them.
- **Fix:** run the backend with `~/bin/mvnx spring-boot:run` (mvnx invokes the
  classworlds launcher directly with native paths). `start.bat` works for the
  user's own PowerShell usage.
- **Prevention:** on this machine, `mvnx` is the Maven entry point for everything.

### 1.3 Orphaned Java holds :8080 on Windows
- **When:** recurring, every test cycle.
- **Symptom:** you kill Maven but `netstat` still shows a `java.exe` LISTENING on
  8080; the next boot fails with "port already in use".
- **Root cause:** `mvnx spring-boot:run` forks a JVM that survives the Maven
  process being killed.
- **Fix:** `netstat -ano | grep :8080` → `taskkill /F /PID <pid>`.
- **Prevention:** after every test shutdown, check `:8080` (backend) and `:8081`
  (Metro/Expo) are free before declaring hygiene done.

---

## 2. Backend — database & seeder

### 2.1 Hibernate `ddl-auto=update` never adds NEW columns to existing tables
- **When:** Phase 6 bring-up (staff_members), P1 (patients, Aug 18 2026).
- **Symptom:** entity has a new field, boot succeeds, but queries fail with
  "column does not exist" — or the column silently never appears.
- **Root cause:** Hibernate's update mode reliably adds new TABLES but skips new
  COLUMNS on existing tables.
- **Fix:** every new column on an existing table MUST also be added idempotently
  in `DataSeeder.ensureSettingsColumns()`:
  `jdbcTemplate.execute("ALTER TABLE <t> ADD COLUMN IF NOT EXISTS <col> <type>")`.
- **Prevention:** when you add a column to any existing entity, add the ALTER in
  the same commit.

### 2.2 Demo-patient seeder used to CLOBBER clinical fields on every boot
- **When:** Aug 18 2026 (found while verifying P1 persistence).
- **Symptom:** you PATCH a patient's blood type/allergies via the API, restart the
  backend, and the values are back to the seed values.
- **Root cause:** `DataSeeder.ensureDemoPatientsAndQueue()` re-applied
  `setBloodType/setAllergies/setCurrentMedications/setLatestVitals` to EXISTING
  patients on every boot, not just at creation.
- **Fix:** demo clinical values are now applied only inside the `if (p == null)`
  creation branch; existing patients only get a backfilled patient number.
- **Prevention:** any seeder that mutates existing rows is suspect — check whether
  it runs on every boot and whether it would clobber user/API edits. Reboot
  persistence is a required part of verification (P1 test suite).

### 2.3 PostgreSQL CHECK constraints are not updated by ddl-auto
- **When:** Phase 5/6 (PaymentStatus/BookingStatus enum growth).
- **Symptom:** new enum value (e.g. `REFUNDED`) rejected at runtime with a CHECK
  constraint violation even though the Java enum has it.
- **Root cause:** Hibernate's update mode doesn't drop/recreate stale CHECK
  constraints when an enum grows.
- **Fix:** `DataSeeder.ensureConstraintRepair()` drops the stale constraints
  idempotently before entity queries.
- **Prevention:** when adding an enum value, check `ensureConstraintRepair()`
  covers the table.

### 2.4 Local postgres password is a secret — don't try to read it
- **When:** Aug 18 2026.
- **Symptom:** trying to run `psql` for a manual DB fix fails auth; config files
  show the password as masked.
- **Root cause:** credentials are redacted in agent-visible files by design.
- **Fix:** use the API for data mutations (restore seed state via PATCH); only use
  direct SQL when the app itself has no endpoint for it (e.g. append-only vitals
  cleanup) — and if you can't, note the residue honestly instead of forcing it.

---

## 3. Backend — endpoints & contracts

### 3.1 Patient JWT principal IS the patient id
- **When:** P1 design (Aug 18 2026).
- **Detail:** `JwtAuthFilter` stores the JWT `subject` (a `Long`) as the
  `Authentication` principal. Patient-scoped endpoints resolve the caller via
  `(Long) auth.getPrincipal()` — NEVER via a path id. Staff tokens store
  `facilityId` as credentials (`SecurityUtils.requireFacilityId()`).
- **Prevention:** new patient endpoints follow the `PatientProfileController`
  pattern: `@PreAuthorize("hasRole('PATIENT')")` + `currentPatientId()` helper.

### 3.2 The mobile store interfaces ARE the API contract
- **When:** architecture design, P1.
- **Symptom:** DTO field renamed (`medications` vs `currentMedications`,
  `id` vs `bookingId`) → mobile screen renders blank/undefined; web mock and
  backend drift apart.
- **Root cause:** two independently-evolved type systems (web mocks, mobile stores)
  with no shared schema.
- **Fix:** define DTO field names from the mobile store interfaces in
  `ARCHITECTURE.md §5.2` verbatim. No renames, no `{data: ...}` wrappers. When an
  API shape changes, update the mobile store seeds/mocks in the SAME PR.
- **Prevention:** read the relevant mobile store file before defining any DTO.

### 3.3 `POST /api/uploads/images` used to reject patient tokens
- **When:** P2 (Aug 18 2026).
- **Symptom:** patient JWT calling the existing upload endpoint to store an
  insurance card photo gets 403 "Facility context required".
- **Root cause:** `UploadController` called `SecurityUtils.requireFacilityId()`,
  which only staff tokens carry. Patient JWTs have a subject (patient id) but
  no `facilityId` claim.
- **Fix:** staff tokens still store under `{facilityId}/`; patient tokens store
  under `patients/{patientId}/`. Anon stays 401. The returned `url` is then
  PUT onto `/api/patients/me/insurance` as `cardPhotoUri`.
- **Prevention:** any "reuse the upload endpoint" packet must accept PATIENT
  as well as staff — don't assume every authenticated caller has a facility.

### 3.4 Seeded hospitals are PENDING — discovery will be empty
- **When:** P2 (Aug 18 2026).
- **Symptom:** `GET /api/mobile/hospitals` returns `[]` even though Korle Bu
  and Ridge exist in the DB.
- **Root cause:** `Hospital.verificationStatus` defaults to `PENDING`; the
  original seeder never approved them. Discovery lists `APPROVED` only.
- **Fix:** `DataSeeder.ensureDiscoveryDemoData()` approves the known seed
  hospitals (MLSC-* / Korle Bu / Ridge / KNUST) when they are still PENDING
  with no rejection reason, and adds KNUST University Hospital if missing.
  User-registered hospitals that are still PENDING are left alone.
- **Prevention:** if a new discovery endpoint filters on `APPROVED`, seed
  (or backfill) that status in the same packet. Don't approve every PENDING
  row — that would leak unverified registrations.

### 2.5 Do not backfill `pay_by_deadline` from `booking_date`
- **When:** P3 (Aug 18 2026).
- **Symptom:** first boot after adding the column, the expiry job cancelled
  3 existing unpaid demo bookings and released their slots.
- **Root cause:** `booking_date` is the *create* timestamp, not the
  appointment. Rows created days ago got `booking_date + 48h` already in
  the past, so `@Scheduled` treated them as overdue.
- **Fix:** backfill null deadlines with `NOW() + INTERVAL '48 hours'`.
  Only fill NULL — never overwrite a deadline the API already set.
- **Prevention:** any "hours after booking" column must backfill from
  current time (or the appointment instant), not from an old create stamp.

### 3.5 Wire `paymentStatus` is `UNPAID`; the DB enum stays `PENDING`
- **When:** P3 (Aug 18 2026).
- **Symptom:** mobile OutstandingBooking expects `paymentStatus: "UNPAID"`,
  but `PaymentStatus` has been `PENDING` since the first bookings table
  (existing rows + CHECK history). Renaming the enum would break the web
  dashboard and every seeded booking.
- **Fix:** keep `PaymentStatus.PENDING` in the database; map it to `"UNPAID"`
  only on `BookingSummaryResponse`. Do not add an `UNPAID` enum value.
- **Prevention:** when a mobile contract word differs from an existing
  backend enum, map at the DTO — don't grow the enum unless the DB value
  itself must change.

### 3.6 `@EnableScheduling` is required for pay-by auto-cancel
- **When:** P3 (Aug 18 2026).
- **Symptom:** `UnpaidBookingExpiryJob` is on the classpath but unpaid
  bookings sit past `payByDeadline` forever.
- **Root cause:** `@Scheduled` methods are ignored unless the application
  class has `@EnableScheduling`.
- **Fix:** `@EnableScheduling` on `DemoApplication`. GET
  `/api/patients/me/outstanding` also runs the same expiry pass so a
  Payments-screen refresh cancels overdue rows even if the tick hasn't
  fired yet. Staff can set a deadline in the past via
  `PATCH /api/bookings/{id}/pay-by-deadline` (ADMIN only) to verify.
- **Prevention:** any new `@Scheduled` job must land with `@EnableScheduling`
  in the same PR (or confirm it is already on).

### 3.8 Cancelled bookings still own `time_slot_id` (OneToOne unique)
- **When:** P3 (Aug 18 2026).
- **Symptom:** after the expiry job releases a slot (`isBooked=false`),
  availability shows the time as free, but `POST /api/bookings/mobile`
  returns 409 "This record already exists".
- **Root cause:** `Booking.timeSlot` is `@OneToOne`, so `bookings.time_slot_id`
  is unique. Expiry/cancel flips `isBooked` but leaves the cancelled row
  pointing at the slot — a new booking cannot reuse that row.
- **Fix:** `claimSlot` reuses an unbooked TimeSlot only when no booking
  (including cancelled) still references it; otherwise it inserts a new
  TimeSlot for the same doctor/date/time. Availability still keys on
  `isBooked`, so the grid stays correct.
- **Prevention:** never assume "slot unbooked" means "slot row is free to
  attach" under a OneToOne. Check `existsByTimeSlotId` or null the
  association on cancel.

### 3.9 Aza `amount` is pesewas, Pulse stores GHS
- **When:** P4 (Aug 18 2026).
- **Symptom:** charging ₵20.00 as `"amount": 20` would undercharge by 100x
  (or ₵50.00 as 50 instead of 5000).
- **Root cause:** Aza's public developer landing (aza.systems/developers)
  charges ₵50.00 with `"amount": 5000`. That is pesewas (minor units).
  `Booking.amountDue` is `BigDecimal` GHS. The login-walled API explorer
  was not opened in this packet (no key yet).
- **Fix:** convert exactly once in `AzaAmountConverter.toMinorUnits`
  (`movePointRight(2)`). Never send GHS to Aza and never store pesewas on
  `Booking`.
- **Prevention:** any new Aza call goes through the converter. If a later
  key proves the unit is GHS, change only that class.

### 3.10 Aza webhook is permit-all; we verify the session id
- **When:** P4 (Aug 18 2026).
- **Symptom:** Aza cannot send our patient JWT, so a 401 on
  `POST /api/webhooks/aza` would drop every real confirmation.
- **Root cause:** public Aza pages do not document a webhook signature
  header. The packet's "anon → 401 on webhook" check conflicts with
  "permit-all, verify".
- **Fix:** `POST /api/webhooks/aza` is permit-all. We require the session
  id to exist on a `PaymentTransaction` we created. If `AZA_WEBHOOK_SECRET`
  is set we also check `X-Aza-Signature` / `X-Webhook-Secret`. Duplicate
  `checkout.completed` deliveries are no-ops (status already COMPLETED).
- **Prevention:** never put the webhook behind patient JWT. Bookings flip
  PAID only here — never on `POST /payments`.

### 3.11 Aza has no stored-instrument API
- **When:** P4 (Aug 18 2026).
- **Detail:** merchant checkout is hosted (`POST /api/v1/merchant/sessions`
  → `pay.aza.systems/c/cs_...`). There is no tokenized card/MoMo charge
  endpoint. `PaymentMethod` rows are display metadata (network, label,
  last4). `gatewayToken` on the mobile shape maps to the Aza session id
  on `PaymentTransaction`, not a reusable instrument.
- **Prevention:** never treat `methodId` as something you can charge
  without opening a new Aza session.

### 3.7 A department with availability but no doctors cannot be booked
- **When:** P3 (Aug 18 2026).
- **Symptom:** `GET /api/mobile/departments/17/availability` returns a full
  14-day grid (P2 synthesizes slots even with zero doctors) but
  `POST /api/bookings/mobile` fails — `TimeSlot.doctor` is NOT NULL.
- **Root cause:** P2 seeded KNUST General OPD without a doctor.
- **Fix:** `DataSeeder.ensureKnustDoctor()` adds one GP when the department
  has none. Booking still 400s with a guidance message if a department
  truly has no doctors.
- **Prevention:** any department used in the mobile book flow needs at
  least one `Doctor` row. Don't assume availability implies bookability.

---

## 4. Frontend (web) — `housebuoy/pulse-web`

### 4.1 `next build` is a mandatory gate — it catches what lint misses
- **When:** Aug 18 2026 — PRs #14–#17 merged, `npm run build` went RED.
- **Symptom:** `npm run lint` passes (0 errors), but `next build` fails the
  TypeScript type-check.
- **Root cause (two real cases):**
  1. `lib/mock/auth.ts` — `LoginResult.token`, `OtpVerifyResult.token`,
     `finalizeLogin(token)` declared `string`, but the real 2FA flow returns
     `token: null` on the first login step (token comes from `verifyLoginOtp`).
     Fix: widen all three to `string | null`.
  2. `components/dashboard/patients/clinical-record-dialog.tsx` — the dialog's
     `onSubmit` prop type declared `{ medications }` but the payload submits
     `currentMedications` (the `UpdateClinicalRecordInput` contract field, PR #3).
     Fix: align the prop type with the payload.
- **Prevention:** after ANY merge, run `npm run build` (not just lint). See the
  `pulse-post-merge` skill (v1.2+) — build is a definition-of-done gate.

### 4.2 Mock/real swap: `NEXT_PUBLIC_USE_MOCK`
- **Detail:** mock is the DEFAULT unless the env var is the literal string
  `"false"` (`lib/api/*.ts` checks `!== "false"`). Unset or `true` = mock data.
- **Symptom:** you test "live" but see seeded mock data and conclude the backend
  is broken (or vice versa).
- **Fix:** for live E2E: `NEXT_PUBLIC_USE_MOCK=false npm run dev`.
- **Prevention:** never report mock data as live; state which mode you tested in.

### 4.3 Pre-existing npm noise in the working tree
- **When:** Aug 18 2026.
- **Detail:** `.gitignore` + `package-lock.json` may be dirty before you start
  (npm install noise). They are NOT yours — leave them untouched and commit only
  the files you changed.

---

## 5. Mobile — `housebuoy/pulse-mobile`

### 5.1 `npm run lint` exits 1 BY DESIGN — it's a delta check
- **Detail:** prettier `-c` fails on 99 tracked files (pre-existing drift) and
  eslint has 7 deprecation warnings (0 errors). A clean exit is NOT achievable
  today.
- **Verdict rule:** pass = eslint 0 errors AND the prettier warning count did NOT
  grow beyond the 99-file baseline. Never "fix" the drift with `--fix` during
  verification (that's a code change, not a check).

### 5.2 No test suite, no API layer — verify honestly
- **Detail:** no Jest/RNTL config (no test script), zero network calls in `src/`
  (all screens hardcoded/mock). There is NO live E2E against the backend yet.
- **Report rule:** say "no tests configured" and "UI shell — not yet verifiable
  against backend". Never claim tests pass or that mobile↔backend integration
  works.

### 5.3 `expo-doctor` patch mismatches are advisory
- **Detail:** expo 54.0.36 vs ~54.0.37, expo-constants 18.0.13 vs ~18.0.14 —
  reported as notes, not failures. Only `tsc --noEmit` / eslint delta /
  `expo export` gate the verdict.

### 5.4 The availability mock shape is a contract
- **Detail:** `services/mock/hospital-schedule.ts` returns
  `{closedDates, fullDates, slots: Record<'yyyy-MM-dd', {MORNING, AFTERNOON}>}`.
  The mobile app parses that exact shape. Backend availability endpoints MUST
  return it (or a superset) — see ARCHITECTURE.md §8 P2.

---

## 6. Git & workflow

### 6.1 Never push to `main`, never merge — PRs only
- **Detail:** all work goes granular commits (one file per commit, conventional
  commit types) → fresh `feat/*`/`fix/*`/`docs/*` branch → `gh pr create` → the
  owner merges. The agent merges ONLY on explicit go-ahead.

### 6.2 GitHub auto-retarget of stacked PRs can fail
- **When:** Aug 13 2026 — #9–#13 merged into base branches, not `main`.
- **Symptom:** you merge #N and expect #N+1 to re-point at `main`; it stays on
  the old base.
- **Fix:** after ANY stacked-PR merge, verify the whole chain resolves to `main`
  (`git log origin/main --oneline -3`) before continuing.
- **Prevention:** check `baseRefName` on each PR before merging; verify after.

### 6.3 Backticks in `git commit -m` get shell-interpreted
- **When:** Aug 18 2026.
- **Symptom:** commit message body has gaps where `` `next build` `` should be —
  bash executed the backticks as command substitution.
- **Fix:** use SINGLE quotes around `-m` bodies when the message contains
  backticks or `$`; amend unpushed commits to repair messages.
- **Prevention:** single-quote commit messages; verify `git log -1 --format=%B`.

---

## 7. Verification checklist for agents (run before claiming done)

- [ ] `~/bin/mvnx clean compile` → BUILD SUCCESS (never plain `mvnw compile`)
- [ ] New columns added to `DataSeeder.ensureSettingsColumns()`
- [ ] Seeder doesn't clobber existing-row edits (check for `setX` outside the creation branch)
- [ ] Boot → seeder log lines present → API suite (happy + auth-negative: 401 anon / 403 wrong role)
- [ ] PATCH/update endpoints survive a full restart (reboot persistence)
- [ ] Web: `npm run lint` + `npm run build` (mandatory) both pass on merged `main`
- [ ] Mobile: `tsc --noEmit` exit 0; eslint 0 errors; prettier delta ≤ 99 files; `expo export` → "Exported: dist"
- [ ] DB restored to seeded state (revert test mutations); test instances shut down; ports 8080/8081 free
- [ ] This doc updated with any NEW pitfall you hit (with the fix!)
- [ ] Commits granular + conventional; fresh branch; PR opened; nothing merged by the agent
