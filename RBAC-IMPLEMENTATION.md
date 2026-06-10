# Pulse Backend — RBAC Implementation Report

## Role-Based Access Control — Implemented ✅

### 4 Roles

| Role | Source | DB Enum Value | JWT Claim | Spring Security Authority |
|------|--------|--------------|-----------|--------------------------|
| **PATIENT** | Patient signup/login | — | `PATIENT` | `ROLE_PATIENT` |
| **DOCTOR** | Doctor workspace login | — | `DOCTOR` | `ROLE_DOCTOR` |
| **HOSPITAL_ADMIN** | Hospital registration | `HOSPITAL_ADMIN` | `HOSPITAL_ADMIN` | `ROLE_HOSPITAL_ADMIN` |
| **SUPER_ADMIN** | DataSeeder (seeded) | `SUPER_ADMIN` | `SUPER_ADMIN` | `ROLE_SUPER_ADMIN` |

### Role Enforcement Per Endpoint

| Method | Endpoint | PATIENT | DOCTOR | HOSPITAL_ADMIN | SUPER_ADMIN |
|--------|----------|---------|--------|----------------|-------------|
| POST | /api/hospitals/register | — | — | — | — (public) |
| POST | /api/hospitals/login | — | — | — | — (public) |
| POST | /api/hospitals/{id}/departments | ❌ | ❌ | ✅ | ✅ |
| DELETE | /api/hospitals/{id}/departments/{deptId} | ❌ | ❌ | ✅ | ✅ |
| POST | /api/hospitals/{id}/working-hours | ❌ | ❌ | ✅ | ✅ |
| **PUT** | /api/admin/hospitals/{id}/verify | ❌ | ❌ | ❌ | **✅ only SUPER_ADMIN** |
| POST | /api/auth/patient/login | — | — | — | — (public) |
| POST | /api/hospitals/{id}/departments/{deptId}/doctors | ❌ | — | ✅ | ✅ |
| All GET discovery | /api/hospitals, /api/doctors, /api/departments | — | — | — | — (public GET) |
| Everything else | any request | ✅ | ✅ | ✅ | ✅ |
| Patient bookings | /api/patients/* | ✅ | ❌ | ❌ | ✅ |

### How It Works

1. **JwtAuthFilter** extracts `role` claim from JWT → wraps as `ROLE_<value>` authority
2. **@EnableMethodSecurity** enables `@PreAuthorize` annotations
3. **@PreAuthorize("hasRole('SUPER_ADMIN')")** on verify endpoint restricts to super admins only
4. **@PreAuthorize("hasRole('HOSPITAL_ADMIN') or hasRole('SUPER_ADMIN')")** on department/hours endpoints
5. **GlobalExceptionHandler** returns JSON 403 with `AccessDeniedException`

### Verified With Tests
- ✅ SA login → SUPER_ADMIN (200)
- ✅ HA login → HOSPITAL_ADMIN (200)
- ✅ SA creates department → 400 (validation, not 403)
- ✅ HA tries to verify license → 403 Forbidden
- ✅ SA verifies license → 200 Approved

### Authentication Credentials (Dev Only)

**Super Admin:**
- Email: `superadmin@pulse.gh`
- Password: `superadmin123`
- Role: SUPER_ADMIN (not tied to any hospital)

**Hospital Admins (per registered hospital):**
- Email: varies (created during hospital registration)
- Password: `admin123` (auto-reset on every restart for dev)
- Role: HOSPITAL_ADMIN (tied to their hospital)

### Configuration Changes

| File | Change |
|------|--------|
| `AdminRole.java` | Added `HOSPITAL_ADMIN` and `SUPER_ADMIN` |
| `HospitalAdmin.java` | `hospital` field now `nullable = true` (super admin has no hospital) |
| `SecurityConfig.java` | Added `@EnableMethodSecurity`, `authenticationEntryPoint()`, `accessDeniedHandler()` |
| `JwtAuthFilter.java` | Wraps JWT role as `ROLE_<role>` authority |
| `HospitalController.java` | `@PreAuthorize` on department/hours endpoints |
| `HospitalAdminController.java` | Changed `@PostMapping` → `@PutMapping`, `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| `BookingController.java` | `@PreAuthorize` on booking endpoints |
| `DataSeeder.java` | Seeds super admin; resets hospital admin passwords to "admin123" on each restart |
| `GlobalExceptionHandler.java` | Handles `AccessDeniedException` → 403 JSON |

### Future Phases

- **Phase 2:** Patient registration/discovery endpoints with PATIENT role
- **Phase 3:** Full booking/queue system with role-specific scoping (patients see their bookings, doctors see their schedule)
- **Phase 4:** Audit logging for sensitive operations (verify, delete)