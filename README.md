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
│   ├── HospitalController.java  # Hospital listing
│   ├── DepartmentController.java# Department listing
│   ├── DoctorController.java    # Doctor listing
│   ├── TimeSlotController.java  # Available slot listing
│   └── HelloController.java     # Health check endpoints
├── service/
│   ├── AuthService.java         # Patient signup (OTP) + login
│   ├── DoctorAdminService.java  # Doctor creation + login
│   └── BookingService.java      # Booking logic + discovery flow
├── model/
│   ├── Patient.java             # Patient / end-user accounts
│   ├── Doctor.java              # Doctor / admin accounts
│   ├── Hospital.java            # Healthcare facility
│   ├── Department.java          # Medical department (per hospital)
│   ├── Booking.java             # Appointment booking record
│   ├── TimeSlot.java            # Doctor's available time slots
│   ├── PendingRegistration.java # Temp storage for OTP signup flow
│   ├── BookingStatus.java       # PENDING_PAYMENT / CONFIRMED / CANCELLED
│   ├── PaymentStatus.java       # PENDING / PAID / FAILED
│   └── Gender.java              # MALE / FEMALE / OTHER
├── dto/
│   ├── SignupRequest.java       # Patient signup payload
│   ├── VerifyOtpRequest.java    # OTP verification payload
│   ├── PatientLoginRequest.java # Patient login (ghanaCard OR phone)
│   ├── DoctorLoginRequest.java  # Doctor login (workspaceId + email)
│   ├── CreateDoctorRequest.java # Admin creates a doctor account
│   ├── AuthResponse.java        # JWT token response
│   ├── BookingRequest.java      # Create booking payload
│   ├── BookingResponse.java     # Booking summary response
│   ├── PaymentUpdateRequest.java# Update booking payment status
│   └── ApiResponse.java         # Generic success/error response
├── repository/
│   ├── PatientRepository.java
│   ├── DoctorRepository.java
│   ├── HospitalRepository.java
│   ├── DepartmentRepository.java
│   ├── BookingRepository.java
│   ├── TimeSlotRepository.java
│   └── PendingRegistrationRepository.java
└── DemoApplication.java         # Spring Boot entry point
```

---

## 🔐 Authentication System

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

## ❤️ Health Check

| Endpoint | Method | Auth | Description |
|----------|--------|:----:|-------------|
| `/api/hello?name=pulse` | GET | ❌ Public | Ping / greeting test |
| `/api/status` | GET | ❌ Public | Health status (DB connected, etc.) |

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
- [ ] Swagger / OpenAPI documentation
- [ ] Input validation (`@Valid` / Jakarta Bean Validation)
- [ ] CORS configuration for web + mobile clients
- [ ] Unit & integration tests
- [ ] Email notifications & reminders
- [ ] Prescription & medical records module

---

## 👤 Lead Backend Engineer

**Marvinphil Annorbah** — Full Stack Developer (Python FastAPI + TypeScript Node.js/Express + Spring Boot)