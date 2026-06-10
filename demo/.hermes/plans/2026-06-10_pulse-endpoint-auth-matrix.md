# Pulse Backend — Endpoint Auth Matrix

> **Purpose:** Maps every API endpoint to the role(s) that can access it, plus the JWT role claim each login method issues.

---

## 1. Token Types & Their Claims

### Super Admin Token
| Field | Value |
|-------|-------|
| **Login endpoint** | `POST /api/hospitals/login` |
| **Role claim** | `"SUPER_ADMIN"` |
| **Extra claims** | `hospitalId: 0` |
| **Payload** | `{ "email": "superadmin@pulse.gh", "password": "superadmin123" }` |
| **Response** | `{ "token": "jwt...", "role": "SUPER_ADMIN", "userId": 1, "message": "Login successful" }` |

### Hospital Admin Token
| Field | Value |
|-------|-------|
| **Login endpoint** | `POST /api/hospitals/login` |
| **Role claim** | `"HOSPITAL_ADMIN"` |
| **Extra claims** | `hospitalId` — the hospital the admin manages |
| **Payload** | `{ "email": "info@korlebu.gov.gh", "password": "..." }` |
| **Response** | `{ "token": "jwt...", "role": "HOSPITAL_ADMIN", "userId": 1, "message": "Login successful" }` |

### Doctor Token
| Field | Value |
|-------|-------|
| **Login endpoint** | `POST /api/auth/admin/login` |
| **Role claim** | `"DOCTOR"` |
| **Extra claims** | None (just `userId`) |
| **Payload** | `{ "workspaceId": "CARDIO-DOC-001", "email": "yaw.appiah@korlebu.gov.gh", "password": "..." }` |
| **Response** | `{ "token": "jwt...", "role": "DOCTOR", "userId": 1, "message": "Login successful" }` |

### Patient Token
| Field | Value |
|-------|-------|
| **Login endpoint** | `POST /api/auth/patient/login` |
| **Role claim** | `"PATIENT"` |
| **Extra claims** | None (just `userId`) |
| **Payload** | `{ "identifier": "GHA-123456789-0", "password": "..." }` |
| **Response** | `{ "token": "jwt...", "role": "PATIENT", "userId": 1, "message": "Login successful" }` |

---

## 2. Access Matrix

| # | Endpoint | Method | Public | Patient | Doctor | Hospital Admin | Super Admin | Notes |
|---|----------|--------|:------:|:-------:|:------:|:--------------:|:-----------:|-------|
| | **Health & Status** | | | | | | | |
| 1 | `/api/hello?name=pulse` | GET | ✅ | ✅ | ✅ | ✅ | ✅ | Greeting test |
| 2 | `/api/status` | GET | ✅ | ✅ | ✅ | ✅ | ✅ | Health status |
| | **Patient Auth** | | | | | | | |
| 3 | `/api/auth/patient/signup` | POST | ✅ | ✅ | ✅ | ✅ | ✅ | Start OTP registration |
| 4 | `/api/auth/patient/verify-otp` | POST | ✅ | ✅ | ✅ | ✅ | ✅ | Verify OTP |
| 5 | `/api/auth/patient/login` | POST | ✅ | ✅ | ✅ | ✅ | ✅ | Login → patient JWT |
| | **Doctor / Admin Auth** | | | | | | | |
| 6 | `/api/auth/admin/create-doctor` | POST | ✅ | ✅ | ✅ | ✅ | ✅ | Create new doctor |
| 7 | `/api/auth/admin/login` | POST | ✅ | ✅ | ✅ | ✅ | ✅ | Login → doctor JWT |
| | **Hospital Registration & Auth** | | | | | | | |
| 8 | `/api/hospitals/register` | POST | ✅ | ✅ | ✅ | ✅ | ✅ | Register new hospital |
| 9 | `/api/hospitals/login` | POST | ✅ | ✅ | ✅ | ✅ | ✅ | Hospital login |
| | **Discovery (Public GET)** | | | | | | | |
| 10 | `/api/hospitals` | GET | ✅ | ✅ | ✅ | ✅ | ✅ | List hospitals |
| 11 | `/api/hospitals/{id}` | GET | ✅ | ✅ | ✅ | ✅ | ✅ | Hospital details |
| 12 | `/api/hospitals/{id}/departments` | GET | ✅ | ✅ | ✅ | ✅ | ✅ | Departments |
| 13 | `/api/hospitals/{id}/working-hours` | GET | ✅ | ✅ | ✅ | ✅ | ✅ | Working hours |
| 14 | `/api/departments/{id}/doctors` | GET | ✅ | ✅ | ✅ | ✅ | ✅ | Doctors |
| 15 | `/api/doctors/{id}/slots` | GET | ✅ | ✅ | ✅ | ✅ | ✅ | Time slots |
| | **Hospital Management** | | | | | | | |
| 16 | `POST /api/hospitals/{id}/departments` | POST | ❌ | ❌ | ❌ | ✅ | ✅ | Create department |
| 17 | `DELETE /api/hospitals/{id}/departments/{deptId}` | DELETE | ❌ | ❌ | ❌ | ✅ | ✅ | Delete department |
| 18 | `PUT /api/hospitals/{id}/working-hours` | PUT | ❌ | ❌ | ❌ | ✅ | ✅ | Set hours |
| | **Super Admin Only** | | | | | | | |
| 19 | `/api/admin/hospitals/{id}/verify` | PUT | ❌ | ❌ | ❌ | ❌ | ✅ | Verify license |
| | **Booking** | | | | | | | |
| 20 | `POST /api/bookings` | POST | ❌ | ✅ | ✅ | ❌ | ✅ | Create booking |
| 21 | `GET /api/bookings/{id}` | GET | ❌ | ✅ | ✅ | ✅ | ✅ | Booking summary |
| 22 | `PATCH /api/bookings/{id}/payment` | PATCH | ❌ | ✅ | ❌ | ❌ | ✅ | Update payment |

### Legend
- ✅ — Access granted
- ❌ — Access denied (returns 403 with error message)

---

## 3. Key Details

### ✅ Role Enforcement Is Active
All protected endpoints are gated by `@PreAuthorize` annotations via Spring's `@EnableMethodSecurity`. Attempting an endpoint with the wrong role returns:
```json
{
  "status": 403,
  "message": "Access denied. You don't have permission to perform this action.",
  "errors": null,
  "timestamp": "..."
}
```

### How Roles Map to JWT
The `JwtAuthFilter` adds `ROLE_` prefix to the role claim from the JWT. So:
- JWT role `"HOSPITAL_ADMIN"` → Spring authority `ROLE_HOSPITAL_ADMIN`
- `@PreAuthorize("hasRole('HOSPITAL_ADMIN')")` checks for `ROLE_HOSPITAL_ADMIN`

### Seed Login Credentials

| Role | Login Endpoint | Credentials |
|------|----------------|-------------|
| **Super Admin** | `POST /api/hospitals/login` | `email: superadmin@pulse.gh`, `password: superadmin123` |
| **Doctor** | `POST /api/auth/admin/login` | `workspaceId: CARDIO-DOC-001`, `email: yaw.appiah@korlebu.gov.gh`, `password: admin123` |
| **Hospital Admin** | `POST /api/hospitals/login` | Email varies per hospital (e.g. `admin@testhospital.gh`), Password: `admin123` (auto-reset on restart by DataSeeder) |

> ✅ **Verified:** Role-based access control tested and passing. Super Admin can manage departments & verify licenses. Hospital Admin can manage departments but **cannot** verify licenses (returns 403).

### Where Tokens Go
All protected endpoints expect the token in the `Authorization` header:
```
Authorization: Bearer <token>
```

---

## 4. Sample Payloads

### 4.1 `GET /api/doctors/{id}/slots` — List Available Slots

**Method:** `GET`
**Auth:** Public (no token required)
**Request body:** None

**Query parameter:**

| Param | Type | Required | Example | Description |
|-------|------|----------|---------|-------------|
| `date` | `string` (ISO date) | ✅ | `2026-06-10` | Date to query slots for |

**Sample curl:**
```bash
curl "http://localhost:8080/api/doctors/1/slots?date=2026-06-10"
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "date": "2026-06-10",
    "startTime": "08:00:00",
    "endTime": "08:20:00",
    "booked": false,
    "doctor": {
      "id": 1,
      "firstName": "Yaw",
      "lastName": "Appiah",
      "specialization": "Interventional Cardiology"
    }
  },
  { "id": 2, "date": "2026-06-10", "startTime": "08:20:00", "endTime": "08:40:00", "booked": false },
  { "id": 3, "date": "2026-06-10", "startTime": "08:40:00", "endTime": "09:00:00", "booked": false }
]
```

**Seeded slots:** 27 per doctor per day (8:00 AM – 5:00 PM, 20-min intervals), for 3 days starting today.

---

### 4.2 `PUT /api/admin/hospitals/{id}/verify` — Verify License (Super Admin Only)

**Method:** `PUT`
**Auth:** Bearer token with `SUPER_ADMIN` role

**Sample curl:**
```bash
curl -X PUT "http://localhost:8080/api/admin/hospitals/3/verify" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <super_admin_token>" \
  -d '{"status": "APPROVED", "notes": "All documents verified"}'
```

**Request body:**
```json
{
  "status": "APPROVED",
  "notes": "All documents verified"
}
```

| Field | Type | Values | Required |
|-------|------|--------|----------|
| `status` | `string` | `APPROVED` or `REJECTED` | ✅ |
| `notes` | `string` | Free text | ❌ (but recommended for REJECTED) |

**Response (200 OK):**
```json
{
  "id": 3,
  "name": "Korle Bu Teaching Hospital",
  "licenseNumber": "MLSC-KB-2020-001",
  "verificationStatus": "APPROVED",
  "address": "Guggisberg Ave, Accra"
}
```

**403 error (wrong role):**
```json
{
  "status": 403,
  "message": "Access denied. You don't have permission to perform this action.",
  "errors": null,
  "timestamp": "2026-06-10T13:00:00.000"
}
```