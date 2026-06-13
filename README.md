# Pulse Backend

A Spring Boot-based backend for Pulse — a full-stack healthcare appointment booking platform supporting both web and mobile clients.

---

## 🏗 Tech Stack

| Tech | Version |
|------|---------|
| **Spring Boot** | 4.0.4 |
| **Java** | 25 |
| **Database** | PostgreSQL |
| **Build** | Maven |
| **Security** | Spring Security + JWT (jjwt 0.12.6) |
| **ORM** | Spring Data JPA / Hibernate |

---

## 📂 Project Structure

```
src/main/java/com/example/demo/
├── config/
│   ├── SecurityConfig.java      # Spring Security filter chain
│   ├── JwtUtil.java             # JWT token generation & validation
│   ├── JwtAuthFilter.java       # Bearer token extraction from requests
│   └── DataSeeder.java          # Seeds test data on startup
├── controller/
│   ├── AuthController.java      # Patient auth endpoints
│   ├── AdminController.java     # Doctor/Admin auth + management
│   ├── BookingController.java   # Booking CRUD
│   ├── DepartmentController.java# Department listing
│   ├── DoctorController.java    # Doctor listing
│   ├── HelloController.java     # Health check endpoints
│   ├── HospitalController.java  # Hospital CRUD + department/hours mgmt
│   ├── HospitalAdminController.java # Super admin verify endpoint
│   └── TimeSlotController.java  # Available slot listing
├── service/
│   ├── AuthService.java         # Patient signup (OTP) + login
│   ├── BookingService.java      # Booking logic + slot queries
│   ├── DoctorAdminService.java  # Doctor creation + login
│   └── HospitalService.java     # Hospital registration, login, verify, dept mgmt
├── model/
│   ├── AdminRole.java           # STAFF, HOSPITAL_ADMIN, SUPER_ADMIN
│   ├── Booking.java             # Appointment booking record
│   ├── BookingStatus.java       # PENDING_PAYMENT / CONFIRMED / CANCELLED
│   ├── Department.java          # Medical department (per hospital)
│   ├── Doctor.java              # Doctor / admin accounts
│   ├── Gender.java              # MALE / FEMALE / OTHER
│   ├── Hospital.java            # Healthcare facility
│   ├── HospitalAdmin.java       # Hospital admin & super admin accounts
│   ├── Patient.java             # Patient / end-user accounts
│   ├── PaymentStatus.java       # PENDING / PAID / FAILED
│   ├── PendingRegistration.java # Temp storage for OTP signup flow
│   ├── TimeSlot.java            # Doctor's available time slots
│   ├── VerificationStatus.java  # PENDING / APPROVED / REJECTED
│   └── WorkingHours.java        # Weekly schedule per hospital
├── dto/
│   ├── ApiResponse.java         # Generic success/error response
│   ├── AuthResponse.java        # JWT token response
│   ├── BookingRequest.java      # Create booking payload
│   ├── BookingResponse.java     # Booking summary response
│   ├── CreateDoctorRequest.java # Admin creates a doctor account
│   ├── DepartmentRequest.java   # Create department payload
│   ├── DoctorLoginRequest.java  # Doctor login (workspaceId + email)
│   ├── HospitalLoginRequest.java# Hospital admin login payload
│   ├── HospitalRequest.java     # Register hospital payload
│   ├── HospitalResponse.java    # Hospital details response
│   ├── LicenseVerifyRequest.java# Approve/reject license
│   ├── PatientLoginRequest.java # Patient login (ghanaCard OR phone)
│   ├── PaymentUpdateRequest.java# Update booking payment status
│   ├── RegistrationResponse.java# Hospital registration response
│   ├── SignupRequest.java       # Patient signup payload
│   ├── VerifyOtpRequest.java    # OTP verification payload
│   ├── WorkingHoursEntry.java   # Single day entry (dayOfWeek, open/close)
│   └── WorkingHoursRequest.java # Batch working hours update payload
├── repository/
│   ├── BookingRepository.java
│   ├── DepartmentRepository.java
│   ├── DoctorRepository.java
│   ├── HospitalAdminRepository.java
│   ├── HospitalRepository.java
│   ├── PatientRepository.java
│   ├── PendingRegistrationRepository.java
│   ├── TimeSlotRepository.java
│   └── WorkingHoursRepository.java
└── DemoApplication.java         # Spring Boot entry point
```

---

## 🔐 Authentication System

The platform supports **four token types**, each with different login endpoints:

| Token Type | Login Endpoint | Role Claim | Extra Claims | Access Level |
|------------|----------------|------------|--------------|--------------|
| **Patient** | `POST /api/auth/patient/login` | `PATIENT` | — | Book & pay for appointments |
| **Doctor** | `POST /api/auth/admin/login` | `DOCTOR` | — | View bookings & schedule |
| **Hospital Admin** | `POST /api/hospitals/login` | `HOSPITAL_ADMIN` | `hospitalId` | Manage departments & hours |
| **Super Admin** | `POST /api/hospitals/login` | `SUPER_ADMIN` | `hospitalId: 0` | Verify licenses, full access |

> ✅ **Role enforcement is active.** Every protected endpoint is gated by `@PreAuthorize` annotations. Unauthorized attempts return **403** with a descriptive error message.

Seed credentials for Super Admin:
- Email: `superadmin@pulse.gh`
- Password: `superadmin123`

### Patient Auth (OTP-Based)

The patient registration uses a **phone-based OTP** flow:

1. **Signup** → sends OTP to phone (currently logged to console)
2. **Verify OTP** → creates permanent patient account with hashed password
3. **Login** → returns JWT token (24h expiry)

| Endpoint | Method | Auth | Description |
|----------|--------|:----:|-------------|
| `/api/auth/patient/signup` | POST | ❌ Public | Start registration (fullName, phone, password, ghanaCard?) |
| `/api/auth/patient/verify-otp` | POST | ❌ Public | Verify OTP to complete signup |
| `/api/auth/patient/login` | POST | ❌ Public | Login with ghanaCard **or** phone + password → JWT |

**Patient Login Payload:**
```json
{
  "identifier": "GHA-123456789-0",
  "password": "***"
}
```

**Login Response:**
```json
{
  "token": "jwt...",
  "role": "PATIENT",
  "userId": 1,
  "message": "Login successful"
}
```

### Doctor / Admin Auth

Doctors act as admins. They login with a **workspace ID** + **email** + **password**.

| Endpoint | Method | Auth | Description |
|----------|--------|:----:|-------------|
| `/api/auth/admin/create-doctor` | POST | ❌ Public | Create a new doctor account |
| `/api/auth/admin/login` | POST | ❌ Public | Login with workspaceId + email + password |

**Doctor Login Payload:**
```json
{
  "workspaceId": "CARDIO-DOC-001",
  "email": "yaw.appiah@korlebu.gov.gh",
  "password": "***"
}
```

#### Workspace ID Auto-Generation

Doctor workspace IDs are auto-generated in the format `{DEPARTMENT_ABBREV}-DOC-{NNN}`.

- Cardiology → `CARDIO-DOC-001`, `CARDIO-DOC-002`
- Orthopedics → `ORTHO-DOC-001`
- Pediatrics → `PEDS-DOC-001`
- Neurology → `NEURO-DOC-001`

The system queries the existing doctors in that department to find the next sequential number.

**Create Doctor Payload:**
```json
{
  "firstName": "Afua",
  "lastName": "Sarpong",
  "email": "afua.sarpong@korlebu.gov.gh",
  "phone": "+233****4568",
  "specialization": "Pediatric Cardiology",
  "departmentId": 1,
  "hospitalId": 1,
  "licenseNumber": "GC-2024-006",
  "password": "***"
}
```

---

## 🏥 Discovery Flow

Hierarchical browse — hospitals → departments → doctors → available time slots.

| Endpoint | Method | Auth | Description |
|----------|--------|:----:|-------------|
| `/api/hospitals` | GET | ❌ Public | List all hospitals |
| `/api/hospitals/{id}/departments` | GET | ❌ Public | Departments at a hospital |
| `/api/departments/{id}/doctors` | GET | ❌ Public | Doctors in a department |
| `/api/doctors/{id}/slots?date=YYYY-MM-DD` | GET | ❌ Public | Available time slots for a doctor on a date |

---

## 📅 Booking System

| Endpoint | Method | Auth | Description |
|----------|--------|:----:|-------------|
| `/api/bookings` | POST | ✅ JWT | Create a new booking |
| `/api/bookings/{id}` | GET | ✅ JWT | Get booking summary |
| `/api/bookings/{id}/payment` | PATCH | ✅ JWT | Update payment status |

### Booking Flow

1. Patient browses hospitals → departments → doctors → slots
2. Patient picks a slot → `POST /api/bookings`
   - Marks the time slot as booked
   - Sets status to `PENDING_PAYMENT`
   - Calculates `amountDue` from the department's consultation fee
3. `PATCH /api/bookings/{id}/payment` updates payment:
   - `PAID` → booking status becomes `CONFIRMED`
   - `FAILED` → booking status becomes `CANCELLED`, slot is freed

**Create Booking Payload:**
```json
{
  "patientId": 1,
  "timeSlotId": 3
}
```

**Payment Update Payload:**
```json
{
  "paymentStatus": "PAID"
}
```

**Booking Response:**
```json
{
  "bookingId": 1,
  "patientName": "Marvinphil Annorbah",
  "doctorName": "Dr. Yaw Appiah",
  "departmentName": "Cardiology",
  "hospitalName": "Korle Bu Teaching Hospital",
  "appointmentDate": "2026-06-10",
  "startTime": "09:00",
  "endTime": "09:30",
  "bookingDate": "2026-06-07T12:00:00",
  "status": "PENDING_PAYMENT",
  "paymentStatus": "PENDING",
  "amountDue": 350.00
}
```

---

## 🏥 Hospital Workspace Management (Phase 1)

Hospital admins register their facilities, manage departments, configure working hours, and go through license verification.

| Endpoint | Method | Auth | Description |
|----------|--------|:----:|-------------|
| `POST /api/hospitals/register` | POST | ❌ Public | Register a new hospital + create primary admin |
| `POST /api/hospitals/login` | POST | ❌ Public | Hospital admin login → JWT |
| `GET /api/hospitals/{id}` | GET | ❌ Public | Get hospital details |
| `POST /api/hospitals/{id}/departments` | POST | ✅ JWT | Create a department |
| `DELETE /api/hospitals/{id}/departments/{deptId}` | DELETE | ✅ JWT | Delete a department |
| `PUT /api/hospitals/{id}/working-hours` | PUT | ✅ JWT | Set weekly working hours |
| `GET /api/hospitals/{id}/working-hours` | GET | ❌ Public | Get working hours |
| `PUT /api/admin/hospitals/{id}/verify` | PUT | ✅ JWT | Approve or reject license (super admin) |

### Register a Hospital
```json
{
  "name": "Ghana Heart Institute",
  "licenseNumber": "GHI-2026-0042",
  "licenseDocumentUrl": "https://storage.example.com/licenses/ghi-0042.pdf",
  "address": "123 Independence Ave, Accra",
  "latitude": 5.5600,
  "longitude": -0.1900,
  "specialties": "[\"Cardiology\",\"Internal Medicine\"]",
  "capacity": 200,
  "phone": "+233****3456",
  "email": "admin@ghanaheart.org",
  "adminFullName": "Kwame Asante",
  "adminEmail": "kwame.asante@ghanaheart.org",
  "adminPassword": "***",
  "adminPhone": "+233****3456"
}
```

**Response** (201): `{ "status": "success", "message": "...", "hospitalId": 1, "adminId": 1, "token": "jwt..." }`

### Create a Department
```json
{
  "name": "Cardiology",
  "abbreviation": "CARD",
  "description": "Heart and cardiovascular care",
  "consultationFee": 350.00,
  "parentDepartmentId": null
}
```

> ⚠️ This endpoint requires a **HOSPITAL_ADMIN** or **SUPER_ADMIN** token.
>
> ⚠️ Duplicate name check: creating a department with the same name in the same hospital returns **400** with message `"Department 'X' already exists in this hospital"`.

### Set Working Hours
```json
{
  "entries": [
    { "dayOfWeek": 1, "openTime": "08:00", "closeTime": "17:00", "isClosed": false },
    { "dayOfWeek": 2, "openTime": "08:00", "closeTime": "17:00", "isClosed": false },
    { "dayOfWeek": 3, "openTime": "08:00", "closeTime": "17:00", "isClosed": false },
    { "dayOfWeek": 4, "openTime": "08:00", "closeTime": "17:00", "isClosed": false },
    { "dayOfWeek": 5, "openTime": "08:00", "closeTime": "17:00", "isClosed": false },
    { "dayOfWeek": 6, "openTime": "09:00", "closeTime": "13:00", "isClosed": false },
    { "dayOfWeek": 7, "openTime": "00:00", "closeTime": "00:00", "isClosed": true }
  ]
}
```
*dayOfWeek: 1=Mon → 7=Sun*

### Verify Hospital License (Super Admin Only)
```json
{
  "status": "APPROVED",
  "rejectionReason": null
}
```

> 🔒 This endpoint is restricted to **Super Admin** role. A regular `HOSPITAL_ADMIN` or `DOCTOR` token will receive a **403** error.

---

## ⚠️ Error Responses

All errors follow a consistent format:

```json
{
  "status": 400,
  "message": "Human-readable error description",
  "errors": ["List of field-level errors (validation only)"],
  "timestamp": "2026-06-10T..."
}
```

| HTTP Status | Scenario |
|:-----------:|----------|
| **400** | Validation errors, duplicate entries, business logic errors |
| **401** | Missing/invalid/expired JWT |
| **403** | Access denied (insufficient permissions) |
| **409** | Database constraint violations (duplicate record) |
| **500** | Unexpected server errors |

---

## 📖 API Documentation

The API is documented via **OpenAPI 3.0** with an interactive Swagger UI.

### For Your Frontend Team

The latest API spec is committed to the repo at **`docs/openapi.json`**. Your frontend team can:

| Method | How to use |
|--------|------------|
| **Open `docs/index.html`** | Clone the repo, open `docs/index.html` in any browser → full interactive Swagger UI with all endpoints, schemas, and "Try it out" |
| **Import into Postman** | File → Import → choose `docs/openapi.json` → all 21+ endpoints pre-configured |
| **Swagger Editor** | Paste the JSON at [editor.swagger.io](https://editor.swagger.io) to browse interactively |
| **Run the server** | `http://localhost:8080/swagger-ui.html` for the live version with real data |

> ⚡ **Whenever endpoints change**, run the server and regenerate the spec:
> ```bash
> curl -s http://localhost:8080/v3/api-docs | python -m json.tool > docs/openapi.json
> ```
> Then commit + push to keep the team in sync.

---

## 🏥 Endpoint Summary (Updated)

| # | Method | Endpoint | Auth | Required Role |
|---|--------|----------|:----:|:--------------|
| 1 | GET | `/api/hello?name=pulse` | ❌ | — |
| 2 | GET | `/api/status` | ❌ | — |
| 3 | POST | `/api/auth/patient/signup` | ❌ | — |
| 4 | POST | `/api/auth/patient/verify-otp` | ❌ | — |
| 5 | POST | `/api/auth/patient/login` | ❌ | — |
| 6 | POST | `/api/auth/admin/create-doctor` | ❌ | — |
| 7 | POST | `/api/auth/admin/login` | ❌ | — |
| 8 | POST | `/api/hospitals/register` | ❌ | — |
| 9 | POST | `/api/hospitals/login` | ❌ | — |
| 10 | GET | `/api/hospitals` | ❌ | — |
| 11 | GET | `/api/hospitals/{id}` | ❌ | — |
| 12 | GET | `/api/hospitals/{id}/departments` | ❌ | — |
| 13 | GET | `/api/hospitals/{id}/working-hours` | ❌ | — |
| 14 | GET | `/api/departments/{id}/doctors` | ❌ | — |
| 15 | GET | `/api/doctors/{id}/slots` | ❌ | — |
| 16 | POST | `/api/hospitals/{id}/departments` | ✅ | `HOSPITAL_ADMIN` or `SUPER_ADMIN` |
| 17 | DELETE | `/api/hospitals/{id}/departments/{deptId}` | ✅ | `HOSPITAL_ADMIN` or `SUPER_ADMIN` |
| 18 | PUT | `/api/hospitals/{id}/working-hours` | ✅ | `HOSPITAL_ADMIN` or `SUPER_ADMIN` |
| 19 | PUT | `/api/admin/hospitals/{id}/verify` | ✅ | `SUPER_ADMIN` only |
| 20 | POST | `/api/bookings` | ✅ | `PATIENT`, `DOCTOR`, or `SUPER_ADMIN` |
| 21 | GET | `/api/bookings/{id}` | ✅ | Any authenticated |
| 22 | PATCH | `/api/bookings/{id}/payment` | ✅ | `PATIENT` or `SUPER_ADMIN` |
| 23 | POST | `/api/hospitals/{id}/license` | ✅ | `HOSPITAL_ADMIN` or `SUPER_ADMIN` |
| 24 | GET | `/api/hospitals/{id}/license` | ✅ | `HOSPITAL_ADMIN` or `SUPER_ADMIN` |

See `.hermes/plans/2026-06-10_pulse-endpoint-auth-matrix.md` for a detailed breakdown.

---

## 🧪 Seed Data

On first startup, `DataSeeder` automatically populates the database with:

**2 Hospitals:**
| Hospital | Location |
|----------|----------|
| Korle Bu Teaching Hospital | Accra |
| Ridge Hospital | Accra |

**4 Departments:**
| Department | Abbrev | Consultation Fee | Hospital |
|------------|--------|:----------------:|----------|
| Cardiology | CARDIO | GHS 350.00 | Korle Bu |
| Orthopedics | ORTHO | GHS 400.00 | Korle Bu |
| Pediatrics | PEDS | GHS 250.00 | Ridge |
| Neurology | NEURO | GHS 500.00 | Ridge |

**5 Doctors (all with password: `admin123`):**
| Doctor | Workspace ID | Department | Hospital |
|--------|-------------|------------|----------|
| Dr. Yaw Appiah | CARDIO-DOC-001 | Cardiology | Korle Bu |
| Dr. Akua Mensah | CARDIO-DOC-002 | Cardiology | Korle Bu |
| Dr. Kwame Ofori | ORTHO-DOC-001 | Orthopedics | Korle Bu |
| Dr. Esi Quartey | PEDS-DOC-001 | Pediatrics | Ridge |
| Dr. Nana Boateng | NEURO-DOC-001 | Neurology | Ridge |

**405 Time Slots (per restart):**
Generated for each doctor for the next 3 days (today + 2), from 8:00 AM to 5:00 PM in 20-minute intervals. Slots are only seeded if the `time_slots` table is empty.

**Super Admin:**
- Email: `superadmin@pulse.gh`
- Password: `superadmin123`
- Role: `SUPER_ADMIN` (not tied to any hospital)

**Hospital Admin Passwords:**
Reset to `admin123` automatically on every restart for existing hospital accounts.

---

## 🚀 Getting Started

### Prerequisites

- **JDK 25** installed (Eclipse Adoptium)
- **PostgreSQL** running on `localhost:5432`
- **Maven wrapper** (included — `mvnw.cmd` for Windows)

### Setup

1. **Create the database**

```sql
CREATE DATABASE pulse_db;
```

2. **Configure credentials**

Update `application.properties` if your PostgreSQL credentials differ:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/pulse_db
spring.datasource.username=postgres
spring.datasource.password=your_password
```

3. **Start the app**

```powershell
cd D:\Projects\pulse\backend\demo
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
.\mvnw spring-boot:run
```

The app starts on `http://localhost:8080` and seed data is inserted automatically.

---

## 📬 Postman Testing

### Recommended Environment Variables

| Variable | Description |
|----------|-------------|
| `pulse_patient_login` | Patient JWT token (set via Tests tab) |
| `pulse_doctor_login` | Doctor JWT token (set via Tests tab) |

### Tests Script (Patient Login)

Add to **Tests** tab on `POST /api/auth/patient/login`:

```javascript
const response = pm.response.json();
if (response.token) {
    pm.environment.set("pulse_patient_login", response.token);
    console.log("✅ Patient token saved");
}
```

### Tests Script (Doctor Login)

Add to **Tests** tab on `POST /api/auth/admin/login`:

```javascript
const response = pm.response.json();
if (response.token) {
    pm.environment.set("pulse_doctor_login", response.token);
    console.log("✅ Doctor token saved");
}
```

Then in authenticated endpoints, set header to:
```
Authorization: Bearer {{puls...n}}
```

---

## 📋 Configuration Reference

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/pulse_db
spring.datasource.username=postgres
spring.datasource.password=your_password

# Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.format_sql=true

# JWT
jwt.secret=pulse-backend-jwt-secret-key-2026-marvinphil-annorbah-ghana-spring-boot
jwt.expiration=86400000
```

---

## 🔮 Coming Soon (Potential Features)

- [ ] SMS integration for OTP delivery (Twilio / AfricasTalking)
- [ ] Patient appointment history
- [ ] Doctor dashboard (view/manage appointments)
- [ ] Admin CRUD endpoints for hospitals, departments, time slots
- [x] Swagger / OpenAPI documentation
- [ ] CORS configuration for web + mobile clients
- [ ] Unit & integration tests
- [ ] Email notifications & reminders
- [ ] Prescription & medical records module

---

## 👤 Lead Backend Engineer

**Marvinphil Annorbah** — Full Stack Developer (Python FastAPI + TypeScript Node.js/Express + Spring Boot)