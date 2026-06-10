# Pulse v2 — Frontend Developer Guide

## For Mobile (Patient App) + Web (Hospital Admin & Doctor Dashboard)

> **Backend:** Spring Boot 4.0.4 + Java 25 + PostgreSQL | **Base URL:** `http://<host>:8080`

This guide maps every screen in both frontend apps to the exact backend endpoints, request/response shapes, and auth requirements. Use this as your single source of truth for API contracts.

---

## Contents

1. [Authentication & JWT Flow](#1-authentication--jwt-flow)
2. [Mobile App — Patient Screens](#2-mobile-app--patient-screens)
3. [Web Dashboard — Hospital Admin Screens](#3-web-dashboard--hospital-admin-screens)
4. [Web Dashboard — Doctor Screens](#4-web-dashboard--doctor-screens)
5. [Shared Data Models](#5-shared-data-models)
6. [Error Handling Conventions](#6-error-handling-conventions)
7. [Queue System UX (Key Screens)](#7-queue-system-ux)
8. [File Upload Guide](#8-file-upload-guide)
9. [Specialty List](#9-specialty-list)

---

## 1. Authentication & JWT Flow

### How It Works

- User registers/logs in → backend returns a `JWT token`
- Store the token locally (localStorage for web, secure storage for mobile)
- **Every authenticated request** includes:
  ```
  Authorization: Bearer <token>
  ```
- Token expires after **24 hours** (configurable)
- Backend returns `401 Unauthorized` if token is expired/invalid → redirect to login

### Roles in the System

| Role | Used By | Frontend App |
|---|---|---|
| `PATIENT` | Patients | Mobile App |
| `DOCTOR` | Doctors | Web Dashboard |
| `HOSPITAL_ADMIN` | Hospital staff admins | Web Dashboard |
| `SUPER_ADMIN` | Pulse platform admins | Web Dashboard (restricted) |

The token payload includes `role` and `userId`. Use these to control what screens are shown.

### Auth Endpoints

#### POST `/api/auth/patient/login`

Step 1 (signup has already been done via OTP flow - see existing endpoints):

```
POST /api/auth/patient/login
Content-Type: application/json

{
  "identifier": "024XXXXXXX",    // phone or ghanaCard number
  "password": "myPassword123"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "PATIENT",
  "userId": 1,
  "message": "Login successful"
}
```

#### POST `/api/auth/doctor/login`

```
POST /api/auth/doctor/login
Content-Type: application/json

{
  "workspaceId": "CARDIO-DOC-001",
  "email": "dr.mensah@hospital.com",
  "password": "myPassword123"
}
```

Response: Same shape as above, `role: "DOCTOR"`

#### POST `/api/auth/admin/login`

```
POST /api/auth/admin/login
Content-Type: application/json

{
  "email": "admin@hospital.com",
  "password": "myPassword123"
}
```

Response: Same shape, `role: "HOSPITAL_ADMIN"`

#### POST `/api/auth/patient/signup` (existing — OTP flow)

```
POST /api/auth/patient/signup
Content-Type: application/json

{
  "fullName": "Kwame Mensah",
  "phone": "024XXXXXXX",
  "ghanaCard": "GHA-XXXXXXXXX-XXXXXX",
  "password": "myPassword123"
}
```

Response: `{ "status": "success", "message": "OTP sent to 024XXXXXXX. Please verify." }`

#### POST `/api/auth/patient/verify-otp`

```
POST /api/auth/patient/verify-otp
Content-Type: application/json

{
  "phone": "024XXXXXXX",
  "otp": "123456"
}
```

Response: `{ "status": "success", "message": "Phone verified. Account created successfully." }`

---

## 2. Mobile App — Patient Screens

### Screen Map

| Screen | Endpoint(s) | Auth Required |
|---|---|---|
| Splash / Landing | — (check stored token validity via any authenticated call) | No |
| Login | `POST /api/auth/patient/login` | No |
| Signup | `POST /api/auth/patient/signup` → `POST /api/auth/patient/verify-otp` | No |
| Home (Nearby Hospitals) | `GET /api/hospitals/nearby?lat=5.6&lng=-0.2&radius=10` | Yes |
| Search Hospitals | `GET /api/hospitals/nearby?lat=5.6&lng=-0.2&specialty=cardiology` | Yes |
| Hospital Detail | `GET /api/hospitals/{id}` | Yes |
| Available Slots | `GET /api/hospitals/{id}/slots?date=2026-06-10&departmentId=1` | Yes |
| Book Appointment | `POST /api/bookings` | Yes |
| My Appointments | `GET /api/patients/me/bookings` | Yes |
| Queue Status | `GET /api/bookings/{bookingId}/queue-status` (poll every 30s) | Yes |
| Profile | `GET /api/patients/me` | Yes |
| Edit Profile | `PUT /api/patients/me` | Yes |

### 2.1 Home Screen — Nearby Hospitals

```
GET /api/hospitals/nearby?lat=5.6037&lng=-0.1870&radius=10
```

**Query params:**
- `lat`, `lng` — user's current location (from phone GPS)
- `radius` — search radius in km (default: 10)
- `specialty` — optional filter (e.g., `cardiology`)

**Response:**
```json
[
  {
    "id": 1,
    "name": "Korle Bu Teaching Hospital",
    "address": "Korle Bu, Accra",
    "distance": 2.3,
    "distanceUnit": "km",
    "specialties": ["Cardiology", "Internal Medicine", "Pediatrics"],
    "capacity": 500,
    "currentQueueLoad": 12,
    "verified": true,
    "latitude": 5.5402,
    "longitude": -0.2301
  },
  ...
]
```

**UI:**
- Map view with hospital pins (using lat/lng)
- Below: list sorted by distance
- Show specialty badges + distance badge on each card
- "Verified" badge for approved hospitals

### 2.2 Hospital Detail Screen

```
GET /api/hospitals/1
```

**Response:**
```json
{
  "id": 1,
  "name": "Korle Bu Teaching Hospital",
  "address": "Korle Bu, Accra",
  "phone": "+233XXXXXXXXX",
  "email": "info@korlebu.org",
  "specialties": ["Cardiology", "Internal Medicine", "Pediatrics"],
  "capacity": 500,
  "consultationDuration": 20,
  "latitude": 5.5402,
  "longitude": -0.2301,
  "departments": [
    {
      "id": 1,
      "name": "Cardiology",
      "parentDepartment": null,
      "doctors": [
        {
          "id": 1,
          "firstName": "Yaw",
          "lastName": "Mensah",
          "specialization": "Cardiology",
          "consultationDuration": 25
        }
      ]
    },
    {
      "id": 2,
      "name": "Pediatrics",
      "parentDepartment": null,
      "doctors": [
        {
          "id": 2,
          "firstName": "Ama",
          "lastName": "Asante",
          "specialization": "Pediatrics",
          "consultationDuration": 15
        }
      ]
    }
  ],
  "currentQueueLoad": 12
}
```

**UI:**
- Header: hospital name, verified badge, distance
- Contact info card (phone, email)
- Specialties as tag chips
- Department list (expandable) → shows doctors under each
- "Book Appointment" CTA button

### 2.3 Available Slots Screen

```
GET /api/hospitals/1/slots?date=2026-06-10&departmentId=1
```

**Response:**
```json
{
  "date": "2026-06-10",
  "department": "Cardiology",
  "consultationDuration": 20,
  "slots": [
    { "startTime": "09:00", "endTime": "09:20", "available": true },
    { "startTime": "09:20", "endTime": "09:40", "available": false },
    { "startTime": "09:40", "endTime": "10:00", "available": true },
    ...
  ]
}
```

**UI:**
- Date picker (calendar)
- Department selector (from hospital detail)
- Time slot grid (scrollable vertical list or horizontal chips)
- Grey out booked slots, highlight available ones
- Tap slot → proceed to confirm booking

### 2.4 Book Appointment

```
POST /api/bookings
Authorization: Bearer <patient_token>
Content-Type: application/json

{
  "hospitalWorkspaceId": 1,
  "departmentId": 1,
  "date": "2026-06-10",
  "slotStartTime": "09:00"
}
```

Note: No `doctorId` — the hospital assigns the doctor. No `patientId` — extracted from JWT token.

**Response:**
```json
{
  "id": 1,
  "patientName": "Kwame Mensah",
  "department": "Cardiology",
  "hospitalName": "Korle Bu Teaching Hospital",
  "date": "2026-06-10",
  "startTime": "09:00",
  "endTime": "09:20",
  "bookingDate": "2026-06-08T15:30:00",
  "status": "PENDING_PAYMENT",
  "paymentStatus": "PENDING",
  "amountDue": 150.00
}
```

**UI:**
- Confirmation screen showing booking details
- "View My Appointments" button

### 2.5 My Appointments

```
GET /api/patients/me/bookings
Authorization: Bearer <patient_token>
```

**Response:**
```json
[
  {
    "id": 1,
    "hospitalName": "Korle Bu Teaching Hospital",
    "department": "Cardiology",
    "doctorName": "Dr. Yaw Mensah",    // assigned after check-in
    "date": "2026-06-10",
    "startTime": "09:00",
    "endTime": "09:20",
    "status": "CONFIRMED",
    "paymentStatus": "PAID",
    "checkedIn": false,
    "amountDue": 150.00
  },
  ...
]
```

**UI:**
- List of appointments grouped by status (Upcoming, Completed, Cancelled)
- Each card: hospital, department, date, time, status badge
- "Check In" button on upcoming appointments (see queue section)

### 2.6 Queue Status (Polling)

```
GET /api/bookings/1/queue-status
Authorization: Bearer <patient_token>
```

**Response:**
```json
{
  "bookingId": 1,
  "hospitalName": "Korle Bu Teaching Hospital",
  "department": "Cardiology",
  "checkedIn": true,
  "checkInTime": "2026-06-10T08:45:00",
  "queuePosition": 3,
  "peopleAhead": 2,
  "estimatedWaitMinutes": 40,
  "status": "WAITING"
}
```

**Status values:** `NOT_CHECKED_IN`, `WAITING`, `IN_CONSULTATION`, `COMPLETED`, `SKIPPED`

**UI:**
- Show when the patient checks in (see 2.7)
- Large queue position number
- "People ahead: 2 • Est. wait: 40 min"
- Progress bar / visual queue indicator
- **Poll every 30 seconds** — update position and wait time
- When position reaches 0 and status changes to `IN_CONSULTATION`: show "Your turn!" animation

### 2.7 Check In (arrive at hospital)

```
PATCH /api/bookings/1/check-in
Authorization: Bearer <patient_token>
```

**Response:**
```json
{
  "bookingId": 1,
  "checkedIn": true,
  "checkInTime": "2026-06-10T08:45:00",
  "queuePosition": 3,
  "estimatedWaitMinutes": 40,
  "message": "You've checked in. 2 people ahead of you."
}
```

**UI:**
- Button on appointment card: "Check In" (only visible on the appointment date)
- After tapping: confirmation screen → transitions to Queue Status screen
- Recommended: geo-fence check (only allow check-in if patient is within ~1km of hospital)

---

## 3. Web Dashboard — Hospital Admin Screens

### Screen Map

| Screen | Endpoint(s) | Auth Required |
|---|---|---|
| Login | `POST /api/auth/admin/login` | No |
| Registration | `POST /api/hospitals/register` | No |
| Dashboard (Overview) | `GET /api/hospitals/{id}` | HOSPITAL_ADMIN |
| Manage Departments | `PUT /api/hospitals/{id}/departments` | HOSPITAL_ADMIN |
| Manage Doctors | `POST /api/hospitals/{id}/doctors` | HOSPITAL_ADMIN |
| Set Working Hours | `PUT /api/hospitals/{id}/working-hours` | HOSPITAL_ADMIN |
| Live Queue Board | `GET /api/hospitals/{id}/queue` | HOSPITAL_ADMIN |
| Bookings Overview | `GET /api/hospitals/{id}/bookings?date=YYYY-MM-DD` | HOSPITAL_ADMIN |

### 3.1 Hospital Registration Flow

Step 1: Submit registration

```
POST /api/hospitals/register
Content-Type: multipart/form-data

Fields:
  name:                    "Korle Bu Teaching Hospital"
  licenseNumber:           "HFR-2024-00421"
  licenseDocument:         <file upload>      // PDF or image of license
  address:                 "Korle Bu, Accra"
  phone:                   "+233XXXXXXXXX"
  email:                   "admin@korlebu.org"
  specialties:             ["Cardiology", "Pediatrics", "Internal Medicine"]
  capacity:                500
  consultationDuration:    20
  adminName:               "Samuel Asare"
  adminEmail:              "s.asare@korlebu.org"
  adminPassword:           "securePassword123"
  adminPhone:              "+233XXXXXXXXX"
```

Note: This creates both the HospitalWorkspace and the PRIMARY_ADMIN in one call.

**Response:**
```json
{
  "workspaceId": 1,
  "workspaceName": "Korle Bu Teaching Hospital",
  "verificationStatus": "PENDING",
  "adminEmail": "s.asare@korlebu.org",
  "message": "Hospital registered. License verification OTP sent to +233XXXXXXXXX."
}
```

**UI Flow:**
1. Registration form (multi-step wizard)
2. Submit → "Please verify your business phone/email" screen (OTP input)
3. After OTP verification → "Registration submitted for review" screen
4. Admin must wait for Pulse super admin to approve license

**OTP Verification (separate endpoint):**

```
POST /api/hospitals/{id}/verify-otp
Content-Type: application/json

{
  "otp": "123456"
}
```

Response: `{ "status": "success", "message": "Phone verified. Awaiting license approval." }`

### 3.2 Dashboard / Overview

```
GET /api/hospitals/1
Authorization: Bearer <admin_token>
```

Response: Same as hospital detail but with additional admin fields:
```json
{
  ...hospitalFields,
  "verificationStatus": "APPROVED",
  "stats": {
    "totalDepartments": 5,
    "totalDoctors": 23,
    "todayBookings": 45,
    "queueLength": 12,
    "avgWaitMinutes": 40
  },
  "workingHours": {
    "monday":    { "open": "08:00", "close": "17:00" },
    "tuesday":   { "open": "08:00", "close": "17:00" },
    "wednesday": { "open": "08:00", "close": "17:00" },
    "thursday":  { "open": "08:00", "close": "17:00" },
    "friday":    { "open": "08:00", "close": "17:00" },
    "saturday":  { "open": "09:00", "close": "14:00" },
    "sunday":    { "open": null, "close": null }
  }
}
```

### 3.3 Manage Departments & Hierarchy

```
PUT /api/hospitals/1/departments
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "departments": [
    {
      "name": "Cardiology",
      "parentDepartmentId": null,
      "description": "Heart and cardiovascular care"
    },
    {
      "name": "Pediatric Cardiology",
      "parentDepartmentId": 1,       // child of Cardiology
      "description": "Children's heart care"
    },
    {
      "name": "Internal Medicine",
      "parentDepartmentId": null,
      "description": "Adult internal medicine"
    }
  ]
}
```

**Response:** `{ "departments": [...array of department objects with generated IDs] }`

**UI:** Tree/hierarchy view where admin can add parent and child departments.

### 3.4 Manage Doctors

```
POST /api/hospitals/1/doctors
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "firstName": "Yaw",
  "lastName": "Mensah",
  "specialization": "Cardiology",
  "email": "dr.mensah@korlebu.org",
  "phone": "+233XXXXXXXXX",
  "licenseNumber": "MDC-2024-00123",
  "departmentId": 1,
  "consultationDuration": 25,        // per doctor!
  "password": "temporaryPass123"
}
```

**Response:**
```json
{
  "id": 1,
  "workspaceId": "CARDIO-DOC-001",
  "firstName": "Yaw",
  "lastName": "Mensah",
  "message": "Doctor created with workspace ID: CARDIO-DOC-001"
}
```

**Important:** Each doctor gets an auto-generated `workspaceId` (e.g., `CARDIO-DOC-001`). Share this with the doctor — it's their login identifier.

**UI:** Form with department dropdown, "Add Doctor" button. List of current doctors below.

### 3.5 Working Hours

```
PUT /api/hospitals/1/working-hours
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "monday":    { "open": "08:00", "close": "17:00" },
  "tuesday":   { "open": "08:00", "close": "17:00" },
  "wednesday": { "open": "08:00", "close": "17:00" },
  "thursday":  { "open": "08:00", "close": "17:00" },
  "friday":    { "open": "08:00", "close": "17:00" },
  "saturday":  { "open": "09:00", "close": "14:00" },
  "sunday":    { "open": null, "close": null }
}
```

**UI:** Weekly schedule grid with time picker for open/close per day. Closed days = toggle off.

### 3.6 Live Queue Board

```
GET /api/hospitals/1/queue
Authorization: Bearer <admin_token>
```

**Response:**
```json
[
  {
    "queueEntryId": 1,
    "bookingId": 1,
    "patientName": "Kwame Mensah",
    "department": "Cardiology",
    "assignedDoctor": null,           // not assigned yet
    "queuePosition": 1,
    "checkInTime": "2026-06-10T08:45:00",
    "status": "WAITING"
  },
  {
    "queueEntryId": 2,
    "bookingId": 2,
    "patientName": "Ama Serwaa",
    "department": "Cardiology",
    "assignedDoctor": null,
    "queuePosition": 2,
    "checkInTime": "2026-06-10T08:50:00",
    "status": "WAITING"
  }
]
```

**Actions on queue entries:**

```
PATCH /api/queue/{queueEntryId}/advance
// → marks patient as IN_CONSULTATION, optionally assigns a doctor
Body: { "doctorId": 1 }

PATCH /api/queue/{queueEntryId}/complete
// → marks patient as COMPLETED, advances queue

PATCH /api/queue/{queueEntryId}/skip
// → marks patient as SKIPPED (patient not present)
```

**UI (the main hospital dashboard screen):**
- Column-based layout (kanban style):
  - **Checked In** (WAITING) → call patient
  - **In Consultation** (IN_CONSULTATION) → currently being seen
  - **Completed** (COMPLETED) → done for the day
  - **Skipped** (SKIPPED) → missed their turn
- Drag-and-drop between columns (or tap → select action)
- For each patient card: name, department, checked-in time, wait duration

---

## 4. Web Dashboard — Doctor Screens

### Screen Map

| Screen | Endpoint(s) | Auth Required |
|---|---|---|
| Login | `POST /api/auth/doctor/login` | No |
| My Appointments | `GET /api/doctors/me/appointments?date=YYYY-MM-DD` | DOCTOR |
| Appointment Detail | `GET /api/doctors/me/appointments/{id}` | DOCTOR |
| My Queue | `GET /api/doctors/me/queue` | DOCTOR |
| Add Notes | `PATCH /api/doctors/me/appointments/{id}/notes` | DOCTOR |

### 4.1 My Appointments (daily schedule)

```
GET /api/doctors/me/appointments?date=2026-06-10
Authorization: Bearer <doctor_token>
```

**Response:**
```json
[
  {
    "id": 1,
    "patientName": "Kwame Mensah",
    "patientPhone": "024XXXXXXX",
    "timeSlot": "09:00 - 09:20",
    "status": "WAITING",
    "notes": null
  },
  {
    "id": 2,
    "patientName": "Ama Serwaa",
    "patientPhone": "024XXXXXXX",
    "timeSlot": "09:20 - 09:40",
    "status": "WAITING",
    "notes": null
  }
]
```

**Status values:** `WAITING` (not checked in), `READY` (checked in, in queue), `IN_CONSULTATION`, `COMPLETED`, `SKIPPED`

**UI:** Timeline view showing each appointment as a time block, colour-coded by status.

### 4.2 Queue (what the doctor sees)

```
GET /api/doctors/me/queue
Authorization: Bearer <doctor_token>
```

**Response:**
```json
{
  "currentPatient": null,   // or { patientName, timeSlot }
  "nextPatient": {
    "bookingId": 1,
    "patientName": "Kwame Mensah",
    "queuePosition": 1,
    "timeSlot": "09:00 - 09:20"
  },
  "waitingCount": 3
}
```

**UI:** "Next Patient" card with name and estimated time. "Call Next" button that calls `PATCH /api/queue/{queueEntryId}/advance`.

---

## 5. Shared Data Models

These are the JSON shapes the frontend needs to know. All dates/times are returned as strings.

### HospitalWorkspace (public view)

```typescript
interface HospitalWorkspace {
  id: number;
  name: string;
  address: string;
  phone: string;
  email: string;
  specialties: string[];
  capacity: number;
  consultationDuration: number;
  latitude: number;
  longitude: number;
  departments: Department[];
  currentQueueLoad: number;
  verified: boolean;
}
```

### Department

```typescript
interface Department {
  id: number;
  name: string;
  parentDepartmentId: number | null;
  doctors: Doctor[];
}
```

### Doctor

```typescript
interface Doctor {
  id: number;
  firstName: string;
  lastName: string;
  specialization: string;
  consultationDuration: number;
  email?: string;
  phone?: string;
}
```

### Booking

```typescript
interface Booking {
  id: number;
  patientName: string;
  doctorName: string | null;        // null until assigned
  department: string;
  hospitalName: string;
  date: string;                      // "2026-06-10"
  startTime: string;                 // "09:00"
  endTime: string;                   // "09:20"
  bookingDate: string;               // ISO datetime
  status: 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELLED';
  paymentStatus: 'PENDING' | 'PAID' | 'FAILED';
  checkedIn: boolean;
  checkInTime?: string;
  amountDue: number;
}
```

### Queue Status

```typescript
interface QueueStatus {
  bookingId: number;
  hospitalName: string;
  department: string;
  checkedIn: boolean;
  checkInTime?: string;
  queuePosition: number;
  peopleAhead: number;
  estimatedWaitMinutes: number;
  status: 'NOT_CHECKED_IN' | 'WAITING' | 'IN_CONSULTATION' | 'COMPLETED' | 'SKIPPED';
}
```

---

## 6. Database Schema (for reference)

A visual map of how data flows through the backend. Frontend engineers should understand these relationships to know which IDs to send in API calls.

### Entity Relationship Map

```
hospital_workspaces
  ├── 1:N → hospital_admins    (hospital staff who manage the workspace)
  ├── 1:N → departments        (cardiology, pediatrics, etc.)
  ├── 1:N → doctors             (all doctors employed here)
  ├── 1:N → bookings            (appointments made at this hospital)
  └── 1:N → working_hours      (weekly schedule: Mon-Sun open/close times)

departments
  ├── N:1 → departments         (self-referencing: parent department = hierarchy)
  ├── 1:N → doctors             (which doctors work in this department)
  ├── 1:N → time_slots          (available appointment windows)
  └── 1:N → bookings            (appointments booked to this department)

doctors
  ├── 1:N → bookings           (appointments assigned to this doctor, nullable)
  └── N:M → patients           (via patient_doctors join table)

patients
  ├── 1:N → bookings           (their appointment history)
  └── N:M → doctors            (their assigned doctors)

time_slots
  └── 1:1 → bookings           (one booking per slot)

bookings
  └── 1:1 → queue_entries      (queue tracking, created on check-in)
```

### Full SQL Schema

```sql
-- =============================================
-- Pulse v2 — Complete PostgreSQL Schema
-- =============================================

CREATE TABLE hospital_workspaces (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    license_number    VARCHAR(255) UNIQUE NOT NULL,
    license_document_url VARCHAR(500),
    address           VARCHAR(500) NOT NULL,
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,
    specialties       TEXT,
    capacity          INTEGER,
    phone             VARCHAR(50),
    email             VARCHAR(255),
    verification_status VARCHAR(50) DEFAULT 'PENDING'
                      CHECK (verification_status IN ('PENDING','APPROVED','REJECTED')),
    rejection_reason  TEXT,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hospital_admins (
    id                    BIGSERIAL PRIMARY KEY,
    hospital_workspace_id BIGINT NOT NULL REFERENCES hospital_workspaces(id),
    full_name             VARCHAR(255) NOT NULL,
    email                 VARCHAR(255) UNIQUE NOT NULL,
    password              VARCHAR(255) NOT NULL,
    phone                 VARCHAR(50) NOT NULL,
    role                  VARCHAR(50) DEFAULT 'STAFF'
                          CHECK (role IN ('PRIMARY_ADMIN','STAFF'))
);

CREATE TABLE patients (
    id          BIGSERIAL PRIMARY KEY,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    gender      VARCHAR(20) CHECK (gender IN ('MALE','FEMALE','OTHER')),
    email       VARCHAR(255),
    phone       VARCHAR(50) UNIQUE,
    ghana_card  VARCHAR(100) UNIQUE,
    password    VARCHAR(255) NOT NULL,
    address     VARCHAR(500)
);

CREATE TABLE departments (
    id                    BIGSERIAL PRIMARY KEY,
    hospital_workspace_id BIGINT NOT NULL REFERENCES hospital_workspaces(id),
    name                  VARCHAR(255) NOT NULL,
    parent_department_id  BIGINT REFERENCES departments(id),
    description           TEXT,
    consultation_fee      DECIMAL(10,2),
    abbreviation          VARCHAR(20)
);

CREATE TABLE doctors (
    id                    BIGSERIAL PRIMARY KEY,
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    specialization        VARCHAR(255),
    department_id         BIGINT REFERENCES departments(id),
    email                 VARCHAR(255),
    phone                 VARCHAR(50),
    license_number        VARCHAR(100) UNIQUE,
    workspace_id          VARCHAR(50) UNIQUE NOT NULL,
    password              VARCHAR(255) NOT NULL,
    hospital_workspace_id BIGINT REFERENCES hospital_workspaces(id),
    consultation_duration INTEGER DEFAULT 20
);

CREATE TABLE time_slots (
    id            BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL REFERENCES departments(id),
    date          DATE NOT NULL,
    start_time    TIME NOT NULL,
    end_time      TIME NOT NULL,
    is_booked     BOOLEAN DEFAULT FALSE,
    UNIQUE (department_id, date, start_time)
);

CREATE TABLE bookings (
    id                    BIGSERIAL PRIMARY KEY,
    patient_id            BIGINT NOT NULL REFERENCES patients(id),
    doctor_id             BIGINT REFERENCES doctors(id),
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

CREATE TABLE queue_entries (
    id                      BIGSERIAL PRIMARY KEY,
    booking_id              BIGINT UNIQUE NOT NULL REFERENCES bookings(id),
    queue_position          INTEGER,
    check_in_time           TIMESTAMP NOT NULL,
    status                  VARCHAR(50) DEFAULT 'WAITING'
                            CHECK (status IN ('WAITING','IN_CONSULTATION','COMPLETED','SKIPPED')),
    estimated_wait_minutes  INTEGER
);

CREATE TABLE working_hours (
    id                    BIGSERIAL PRIMARY KEY,
    hospital_workspace_id BIGINT NOT NULL REFERENCES hospital_workspaces(id),
    day_of_week           VARCHAR(10) NOT NULL
                          CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    open_time             TIME,
    close_time            TIME,
    UNIQUE (hospital_workspace_id, day_of_week)
);

CREATE TABLE patient_doctors (
    patient_id BIGINT NOT NULL REFERENCES patients(id),
    doctor_id  BIGINT NOT NULL REFERENCES doctors(id),
    PRIMARY KEY (patient_id, doctor_id)
);
```

### Table Details (JSON-friendly view)

#### `hospital_workspaces`

| Field | Type | Notes |
|---|---|---|
| id | Long | PK |
| name | String | Hospital name |
| licenseNumber | String | Unique, HeFRA registration # |
| licenseDocumentUrl | String | URL to uploaded license scan |
| address | String | Full street address |
| latitude | Double | Geocoded from address |
| longitude | Double | Geocoded from address |
| specialties | String[] | JSON array — selected from curated list |
| capacity | Integer | Approx max patients |
| phone | String | |
| email | String | |
| verificationStatus | "PENDING" \| "APPROVED" \| "REJECTED" | |
| rejectionReason | String | Null unless rejected |

#### `hospital_admins`

| Field | Type | Notes |
|---|---|---|
| id | Long | PK |
| hospitalWorkspaceId | Long | FK → hospital_workspaces |
| fullName | String | |
| email | String | Unique, login credential |
| password | String | BCrypt (never returned in API) |
| phone | String | |
| role | "PRIMARY_ADMIN" \| "STAFF" | |

#### `departments`

| Field | Type | Notes |
|---|---|---|
| id | Long | PK |
| hospitalWorkspaceId | Long | FK → hospital_workspaces |
| name | String | e.g., "Cardiology" |
| parentDepartmentId | Long \| null | For hierarchy (e.g., Pediatric Cardiology's parent = Cardiology) |
| description | String | |
| consultationFee | Decimal | e.g., 150.00 GHS |
| abbreviation | String | e.g., "CARDIO" — used for doctor workspace ID generation |

#### `doctors`

| Field | Type | Notes |
|---|---|---|
| id | Long | PK |
| firstName | String | |
| lastName | String | |
| specialization | String | e.g., "Cardiology" |
| departmentId | Long | FK → departments |
| email | String | |
| phone | String | |
| licenseNumber | String | Unique |
| workspaceId | String | Unique, auto-generated: `CARDIO-DOC-001` — **this is the doctor's login ID** |
| hospitalWorkspaceId | Long | FK → hospital_workspaces |
| consultationDuration | Integer | Minutes — **per doctor** (e.g., some do 20 min, some 30 min) |

#### `bookings`

| Field | Type | Notes |
|---|---|---|
| id | Long | PK |
| patientId | Long | FK → patients |
| doctorId | Long \| null | **Null until hospital assigns a doctor** |
| departmentId | Long | FK → departments |
| hospitalWorkspaceId | Long | FK → hospital_workspaces |
| timeSlotId | Long \| null | FK → time_slots (the chosen appointment window) |
| bookingDate | DateTime | When the booking was created |
| status | "PENDING_PAYMENT" \| "CONFIRMED" \| "CANCELLED" | |
| paymentStatus | "PENDING" \| "PAID" \| "FAILED" | |
| amountDue | Decimal | |
| checkedIn | Boolean | |
| checkInTime | DateTime \| null | |

#### `queue_entries`

| Field | Type | Notes |
|---|---|---|
| id | Long | PK |
| bookingId | Long | FK → bookings (one-to-one) |
| queuePosition | Integer | Computed on check-in — booking-order based |
| checkInTime | DateTime | |
| status | "WAITING" \| "IN_CONSULTATION" \| "COMPLETED" \| "SKIPPED" | |
| estimatedWaitMinutes | Integer | Snapshot at check-in |

---

## 7. Error Handling Conventions

All error responses follow this shape:

```json
{
  "status": "error",
  "message": "Human-readable error description"
}
```

### Common HTTP Status Codes

| Code | Meaning | Handle By |
|---|---|---|
| `200 OK` | Success | Proceed normally |
| `400 Bad Request` | Invalid input (missing fields, wrong format) | Show the `message` to user |
| `401 Unauthorized` | Token missing, expired, or invalid | Redirect to login screen |
| `403 Forbidden` | Token valid but insufficient role for this action | Show "Access denied" |
| `404 Not Found` | Resource doesn't exist | Show "Not found" or redirect |
| `409 Conflict` | Duplicate (e.g., phone already registered) | Show the `message` |
| `500 Internal Server Error` | Backend error | Show "Something went wrong, try again" |

### Validation Errors (when `@Valid` is enforced)

```json
{
  "status": "error",
  "message": "Validation failed",
  "errors": {
    "phone": "Phone number is required",
    "password": "Password must be at least 8 characters"
  }
}
```

---

## 8. Queue System UX (Key Screens)

### Patient App — Queue Tracking Widget

After checking in, the patient sees a full-screen queue tracker:

```
┌─────────────────────────────┐
│   Your Position              │
│                              │
│           #3                 │  ← large number
│                              │
│   2 people ahead of you      │
│   Est. wait: ~40 min         │
│                              │
│   ════════░░░░░░░░░          │  ← progress bar
│                              │
│   Hospital: Korle Bu         │
│   Department: Cardiology     │
│   Doctor: Not yet assigned   │
│                              │
│   [Wait, I'll be late]       │  ← skip/notify button
│   [Cancel Appointment]       │
└─────────────────────────────┘
```

**Polling behaviour:**
- Poll `GET /api/bookings/{id}/queue-status` every 30 seconds
- Show skeleton/shimmer if polling fails
- If position decreases → animate the number counting down
- If `status` changes to `IN_CONSULTATION` → play sound/vibration + "Your turn!" screen

### Hospital Dashboard — Live Queue Board

```
┌────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  WAITING   │  │ IN CONSULT   │  │  COMPLETED   │  │   SKIPPED    │
├────────────┤  ├──────────────┤  ├──────────────┤  ├──────────────┤
│ Kwame M.   │  │ Dr. Mensah   │  │ Ama S.       │  │ Kofi A.      │
│ 09:00      │  │ with:        │  │ 09:00        │  │ 09:00        │
│ ⏳ 40 min  │  │ Ama Serwaa   │  │ Completed    │  │ Not present  │
│ ─────────  │  │ Started:     │  │ 08:50        │  │              │
│ Call ➡️    │  │ 09:05        │  │              │  │              │
│            │  │ ⏳ 15 min    │  │              │  │              │
│ Ama S.     │  │              │  │              │  │              │
│ 09:15      │  │              │  │              │  │              │
│ ⏳ 25 min  │  │              │  │              │  │              │
│ ─────────  │  │              │  │              │  │              │
│ Call ➡️    │  │              │  │              │  │              │
└────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
```

Each patient card has a "Call" or "Assign" button that triggers the queue advancement endpoints.

---

## 9. File Upload Guide

### Uploading License Documents (Hospital Registration)

Use `multipart/form-data` (not JSON):

```javascript
// Web example (form data)
const formData = new FormData();
formData.append('name', 'Korle Bu Teaching Hospital');
formData.append('licenseNumber', 'HFR-2024-00421');
formData.append('licenseDocument', fileInput.files[0]);  // PDF or image
formData.append('address', 'Korle Bu, Accra');
// ... other fields

fetch('/api/hospitals/register', {
  method: 'POST',
  body: formData
  // NO Content-Type header — browser sets it with boundary
});
```

**Accepted file types:** PDF, PNG, JPG
**Max file size:** 5MB (configured on backend)

---

## 10. Specialty List

These are the curated specialties hospitals can choose from (dropdown). The list is seeded on the backend, so no endpoint is needed to fetch it — but if needed later, we can add `GET /api/specialties`.

| # | Specialty |
|---|---|
| 1 | General Medicine |
| 2 | Cardiology |
| 3 | Pediatrics |
| 4 | Obstetrics & Gynecology |
| 5 | Orthopedics |
| 6 | Neurology |
| 7 | Ophthalmology |
| 8 | Ear, Nose & Throat (ENT) |
| 9 | Dermatology |
| 10 | Psychiatry |
| 11 | Radiology |
| 12 | Emergency Medicine |
| 13 | Surgery (General) |
| 14 | Urology |
| 15 | Gastroenterology |
| 16 | Pulmonology |
| 17 | Nephrology |
| 18 | Oncology |
| 19 | Anesthesiology |
| 20 | Pathology |
| 21 | Physiotherapy & Rehabilitation |
| 22 | Dentistry |
| 23 | Family Medicine |
| 24 | Internal Medicine |
| 25 | Endocrinology |
| 26 | Hematology |
| 27 | Infectious Diseases |
| 28 | Rheumatology |
| 29 | Geriatrics |
| 30 | Other |

---

## Quick Reference — All Endpoints

### Public (No Auth)

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/auth/patient/signup` | Start patient signup (sends OTP) |
| POST | `/api/auth/patient/verify-otp` | Complete patient signup with OTP |
| POST | `/api/auth/patient/login` | Patient login |
| POST | `/api/auth/doctor/login` | Doctor login |
| POST | `/api/auth/admin/login` | Hospital admin login |
| POST | `/api/hospitals/register` | Register a hospital workspace |

### Patient (Bearer Token: PATIENT)

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/hospitals/nearby?lat=&lng=&radius=&specialty=` | Find hospitals near me |
| GET | `/api/hospitals/{id}` | Hospital detail with departments & doctors |
| GET | `/api/hospitals/{id}/slots?date=&departmentId=` | Available time slots |
| POST | `/api/bookings` | Create a booking |
| GET | `/api/patients/me/bookings` | My appointment history |
| GET | `/api/bookings/{id}/queue-status` | Live queue position |
| PATCH | `/api/bookings/{id}/check-in` | Check in at hospital |
| GET | `/api/patients/me` | View my profile |
| PUT | `/api/patients/me` | Update my profile |

### Hospital Admin (Bearer Token: HOSPITAL_ADMIN)

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/hospitals/{id}` | Dashboard + stats |
| PUT | `/api/hospitals/{id}/departments` | Manage department hierarchy |
| POST | `/api/hospitals/{id}/doctors` | Add a doctor |
| PUT | `/api/hospitals/{id}/working-hours` | Set weekly schedule |
| GET | `/api/hospitals/{id}/queue` | Live queue board |
| PATCH | `/api/queue/{id}/advance` | Call next patient |
| PATCH | `/api/queue/{id}/complete` | End consultation |
| PATCH | `/api/queue/{id}/skip` | Skip patient |

### Doctor (Bearer Token: DOCTOR)

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/doctors/me/appointments?date=` | My schedule |
| GET | `/api/doctors/me/appointments/{id}` | Appointment detail |
| GET | `/api/doctors/me/queue` | See next patient |
| PATCH | `/api/doctors/me/appointments/{id}/notes` | Add consultation notes |

---

*Last updated: June 8, 2026*