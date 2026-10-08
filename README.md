# HealthConnect - Web-Based Medical Portal

Practical implementation for the **SE2030 Software Engineering** group project,
SLIIT Year 2 Semester 1 2026.

**Group:** 2026-Y2-S1-MLB-WEB3G1-07

This is the working system described by the group's earlier analysis and design
deliverables (project proposal, Scrum backlog, use case diagram + UC-01 to
UC-06 scenarios, activity diagrams, sequence diagrams).

---

## Versions used

| | |
|---|---|
| Java | **21** (LTS) |
| Spring Boot | **3.5.5** |
| Build tool | **Maven** |
| Database | **Microsoft SQL Server** (via `mssql-jdbc`), managed in SSMS |
| Views | **Thymeleaf** (plain HTML + one shared `style.css`) |
| Persistence | **Spring Data JPA / Hibernate** |
| Password hashing | **BCrypt** (`spring-security-crypto` only) |

---

## Modules and who owns what

| # | Member | Module | Use case | Main classes |
|---|--------|--------|----------|--------------|
| 1 | Rathnayaka R.M.C.R.B. | Appointment Management (incl. patient registration) | UC-01 Book Appointment | `AppointmentService`, `AppointmentController`, `ReceptionController` |
| 2 | Gunathilake H.R.N.V. | Doctor Consultation Management | UC-02 Record Consultation Notes & Prescription | `ConsultationService`, `ConsultationController` |
| 3 | Ahamed M.N.N. | Pharmacy & Prescription Management | UC-03 Update Medicine Stock / Dispense | `PharmacyService`, `PharmacyController` |
| 4 | Adithya J.M.O. | Telemedicine (Online Consultation) | UC-04 Book Video Consultation | `TelemedicineService`, `TelemedicineController` |
| 5 | Perera M.K.S.N. | Patient Feedback & Support Management | UC-05 View Patient Feedback and Ratings | `FeedbackService`, `FeedbackController` |
| 6 | Vishalan S. | Hospital Administration & Reporting | UC-06 Manage Doctor & Staff Accounts | `AdminService`, `ReportService`, `AdminController` |
| - | *shared by everyone* | Account & Access | Register / Login / Logout / Reset Password / Manage Profile | `AuthService`, `HomeController`, `AuthInterceptor` |

---

## How to run it

### 1. Set up the database first

Follow **`DBMS-Setup-Guide.md`** (supplied alongside this project). It walks
through installing SQL Server and SSMS, creating the `HealthConnect` database,
creating the tables, creating an application login, and filling in the
connection settings.

### 2. Fill in `application.properties`

Open `src/main/resources/application.properties` and replace the three
placeholders:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=HealthConnect;encrypt=true;trustServerCertificate=true
spring.datasource.username=REPLACE_WITH_YOUR_SQL_LOGIN
spring.datasource.password=REPLACE_WITH_YOUR_SQL_PASSWORD
```

### 3. Run it

From the project folder:

```bash
mvn spring-boot:run
```

or open the folder in IntelliJ IDEA / Eclipse / VS Code and run the `main`
method in `HealthConnectApplication.java`.

Then open **http://localhost:8080** in a browser.

### 4. Log in

The first time the application starts against an **empty** database,
`config/DataSeeder.java` inserts demo data: three doctors, three patients,
one of each staff role, eight medicines, past and upcoming appointments, a
recorded consultation with a dispensed prescription, one prescription still
waiting in the pharmacy queue, an online consultation and two pieces of
feedback.

**Every demo account uses the password `health123`.**

| Email | Role |
|---|---|
| `admin@healthconnect.lk` | Hospital Administrator |
| `cardiology@healthconnect.lk` | Doctor (Cardiology) |
| `general@healthconnect.lk` | Doctor (General Medicine) |
| `derma@healthconnect.lk` | Doctor (Dermatology) |
| `reception@healthconnect.lk` | Receptionist |
| `pharmacy@healthconnect.lk` | Pharmacist |
| `support@healthconnect.lk` | Patient Relations Officer |
| `amal@example.com` | Patient |
| `nimali@example.com` | Patient |

New patients can also register themselves at `/register`.

---

## Project structure

```
src/main/java/com/healthconnect/
├── HealthConnectApplication.java   entry point
├── config/      AppConfig (BCrypt bean), AuthInterceptor (role rules),
│                WebConfig, GlobalControllerAdvice, Routes, DataSeeder
├── controller/  one per module + HomeController for account/access
├── service/     ALL business rules live here
├── repository/  Spring Data JPA interfaces
├── model/       @Entity classes + enums
├── dto/         form-backing objects and report rows
└── exception/   BusinessException and its three subclasses

src/main/resources/
├── application.properties
├── static/css/style.css            the single shared stylesheet
└── templates/                      39 Thymeleaf pages + one layout fragment
    ├── fragments/layout.html       head / navbar / messages / footer
    ├── auth/  patient/  doctor/  pharmacy/  reception/  admin/  support/  error/
```

---

## A demo path that touches every module

1. **Patient** (`amal@example.com`) - *Find a Doctor* -> *Book* -> pick a slot ->
   book. Try booking the same slot twice to see the double-booking rule fire.
2. **Receptionist** - *Appointments* -> **Confirm** that booking.
3. **Doctor** (`cardiology@healthconnect.lk`) - *My Queue* -> **History**
   (the `<<include>>`) -> **Start consultation** -> write a diagnosis and add
   a medicine line -> save.
4. **Pharmacist** - *Prescriptions* -> open the new one -> **Mark as dispensed**.
   Check *Inventory*: the stock has gone down. Try the pending Amoxicillin
   prescription to see the insufficient-stock rule.
5. **Patient** again - *Feedback* -> rate the completed visit.
6. **Patient Relations Officer** - *Feedback* -> respond to it.
7. **Administrator** - *Appt. Report* and *Feedback Report* show it all counted.
8. Still logged in as the patient, type `/admin/staff` into the address bar -
   `AuthInterceptor` blocks it.

---

## Design decisions and assumptions

These were choices the brief left open. Each is noted here so they can be
defended in the viva.

1. **Session-based login rather than Spring Security.** The logged-in user is
   a `SessionUser` object in the `HttpSession`; `config/AuthInterceptor`
   maps URL prefixes to allowed roles and blocks the request before the
   controller runs. Passwords are still BCrypt-hashed - only the tiny
   `spring-security-crypto` library is used, not the full framework. The
   reason is that every line of the access-control logic is plain Java that
   the whole group can read and defend.

2. **Video consultations are scheduled session records, not live calls.**
   WebRTC is out of scope, so `VideoSession` stores a generated placeholder
   meeting link and a status (Scheduled / In Progress / Completed / Cancelled).
   Booking one reuses `AppointmentService` completely - the same slot,
   working-hours and double-booking rules apply - matching the
   "Conduct Consultation" generalisation in the use case diagram.

3. **Password reset does not send email or SMS** (both explicitly out of
   scope). Identity is proved with the registered email plus the registered
   phone number, then the password is changed directly.

4. **There is no separate `PatientRecord` table.** A patient's medical history
   is a query over the consultations attached to that patient's appointments
   (`ConsultationRepository.findPatientHistory`). This keeps the
   `<<include>> View Patient Medical History` step a real, working query
   without duplicating data.

5. **Appointments are on a fixed 30-minute grid** (`AppointmentService.SLOT_MINUTES`)
   and can be booked up to 60 days ahead (`MAX_DAYS_AHEAD`). The brief did not
   specify a slot length; this is the simplest interpretation that makes
   "the slot is already taken" a meaningful check.

6. **Doctor availability is a comma-separated day list plus a from/to time**
   (`MONDAY,TUESDAY,...` / `09:00`-`13:00`) rather than a separate availability
   table. One column keeps the SQL Server schema small and readable in SSMS.

7. **The Patient Relations Officer can respond to feedback.** The actor is in
   the group's use case diagram, and UC-05 is "Feedback **& Support**
   Management, so two fields (`officer_response`, `responded_at`) were added
   to `Feedback` to give that actor a real screen. No new table.

8. **Staff accounts are deactivated, never deleted**, so appointment and
   consultation history stays intact. The role of an existing account cannot
   be changed, because a doctor carries a profile row and history that the
   other roles do not.

9. **Reports count in a loop over doctors** rather than with one `GROUP BY`
   query. At this data size the loop is clearer to read and to explain; the
   numbers are still queried live, never hard-coded.

---

## Where the two companion documents fit

- **`DBMS-Setup-Guide.md`** - SQL Server + SSMS from nothing: install, create
  the database, the full `CREATE TABLE` script, the application login, TCP/IP,
  the exact JDBC settings, troubleshooting, and how to verify writes are
  landing.
- **`Java-Code-Walkthrough.md`** - how the code works, taught through the
  appointment booking flow, with a closing section mapping the worked example
  onto each member's own module.
