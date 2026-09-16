# HealthConnect - Java Code Walkthrough

**A guide for explaining this codebase to the rest of Group 07.**

Everyone reading this already knows Java and OOP from SE1020. Nobody needs to
have seen Spring Boot before. By the end you should be able to open any file in
this project, say what layer it belongs to, and explain what it does.

The whole document uses **one worked example: booking an appointment**
(Rathnayaka's Appointment Management module, UC-01). Every other module is the
same five classes with different names, so once this one flow makes sense, the
rest is pattern-matching. [Section 9](#9-how-this-maps-to-your-module) says
exactly which of your classes lines up with which of these.

---

## Contents

1. [The request flow, end to end](#1-the-request-flow-end-to-end)
2. [Entity classes - the tables](#2-entity-classes---the-tables)
3. [Repository interfaces - the queries you never write](#3-repository-interfaces---the-queries-you-never-write)
4. [Service classes - where the rules live](#4-service-classes---where-the-rules-live)
5. [Controller classes - the traffic police](#5-controller-classes---the-traffic-police)
6. [Thymeleaf templates - the HTML](#6-thymeleaf-templates---the-html)
7. [DTOs - why the form is not the entity](#7-dtos---why-the-form-is-not-the-entity)
8. [Validation and error handling](#8-validation-and-error-handling)
9. [How this maps to your module](#9-how-this-maps-to-your-module)
10. [Questions you might get asked](#10-questions-you-might-get-asked)

---

## 1. The request flow, end to end

Here is the entire architecture in one sentence, and it is worth memorising:

> **Browser -> Controller -> Service -> Repository -> Database**, and then all
> the way back out again.

Concretely, when a patient presses **Confirm booking**:

```
  1. BROWSER      POST /patient/appointments/book
                  with doctorId=2, date=2026-09-20, slot=09:30
                        |
                        v
  2. CONTROLLER   AppointmentController.book(...)
                  Spring has already turned the form fields into an
                  AppointmentForm object for us. The controller does not
                  decide anything - it just hands the form to the service.
                        |
                        v
  3. SERVICE      AppointmentService.bookAppointment(patient, form)
                  ALL the thinking happens here: is the date in the past?
                  does the doctor work that day? is the slot already taken?
                        |
                        v
  4. REPOSITORY   appointmentRepository.existsBy...(...)  -> asks the DB
                  appointmentRepository.save(appointment)  -> writes the DB
                        |
                        v
  5. DATABASE     SQL Server executes SELECT and INSERT
                        |
                        v  (back up the chain)
  6. CONTROLLER   got an Appointment back -> "redirect:/patient/appointments"
                  got a BookingException -> put the message in the model and
                                            re-show the booking page
                        |
                        v
  7. THYMELEAF    renders templates/patient/appointments.html into HTML
                        |
                        v
  8. BROWSER      shows the page
```

### Why bother with four layers?

Because each layer has exactly one job, and that makes the code testable and
changeable:

| Layer | Job | Never does |
|---|---|---|
| **Controller** | Read the request, call a service, choose which page to show | Business rules, SQL |
| **Service** | Business rules, transactions | Know about HTTP, HTML or sessions |
| **Repository** | Talk to the database | Business rules |
| **Entity** | Be a row in a table | Anything clever |

The practical payoff: the *exact same* `AppointmentService.bookAppointment` is
called from the patient's booking page **and** from the telemedicine module's
video booking. One set of rules, two screens. If the rules had been written in
the controller, Adithya would have had to copy-paste them and they would have
drifted apart by the time of the demo.

### How Spring finds all these classes

You never write `new AppointmentService(...)`. At startup Spring scans the
`com.healthconnect` package, finds every class marked `@Controller`,
`@Service`, `@Component` or `@Configuration`, creates one object of each, and
**injects** them into whoever asks for them through a constructor. That is what
this is:

```java
@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    // Spring sees this single constructor and passes in the two repositories.
    public AppointmentService(AppointmentRepository appointmentRepository,
                              DoctorRepository doctorRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
    }
```

This is **dependency injection**. The class says what it needs; Spring supplies
it. You may have seen `@Autowired` on fields in tutorials - constructor
injection like the above is the modern, preferred style, and it lets the fields
be `final`.

---

## 2. Entity classes - the tables

**File:** `model/Appointment.java`

An entity is a plain Java class with annotations that say "this class is a
database table, and these fields are its columns". Hibernate reads those
annotations and writes the SQL for us.

```java
@Entity                             // this class maps to a table
@Table(name = "appointment")        // ...called "appointment"
public class Appointment {

    @Id                                                  // primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // SQL Server IDENTITY -
    @Column(name = "appointment_id")                     // the DB assigns the number
    private Long id;

    @ManyToOne(optional = false)                    // many appointments, one patient
    @JoinColumn(name = "patient_id", nullable = false)   // the FK column
    private Patient patient;

    @ManyToOne(optional = false)                    // many appointments, one doctor
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "appointment_date_time", nullable = false)
    private LocalDateTime appointmentDateTime;

    @Enumerated(EnumType.STRING)                    // store the NAME, not the position
    @Column(name = "appointment_type", nullable = false, length = 20)
    private AppointmentType type = AppointmentType.IN_PERSON;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.PENDING;
```

### Annotation by annotation

| Annotation | What it does |
|---|---|
| `@Entity` | Marks the class as a persistent table. Without it, Hibernate ignores the class completely. |
| `@Table(name = "...")` | The table name. Without it the table would be named after the class. We name `User` -> `users`, because `USER` is a **reserved word** in SQL Server. |
| `@Id` | This field is the primary key. Every entity needs exactly one. |
| `@GeneratedValue(strategy = IDENTITY)` | The database generates the value. `IDENTITY` is the right choice for SQL Server's `IDENTITY(1,1)` columns. You leave `id` null when creating; it is filled in after `save()`. |
| `@Column(name, nullable, length, unique)` | Column name and constraints. `length = 100` becomes `NVARCHAR(100)`. |
| `@Enumerated(EnumType.STRING)` | **Important.** Stores `"CONFIRMED"` rather than `1`. Without `STRING` it stores the enum's *position*, and the day somebody inserts a new constant in the middle of the enum, every existing row silently means something different. |

### The relationships

This is the part most people find confusing, so here it is slowly.

```java
// In Appointment.java - the "many" side. This one OWNS the foreign key.
@ManyToOne(optional = false)
@JoinColumn(name = "doctor_id", nullable = false)
private Doctor doctor;
```

```java
// In Doctor.java - the "one" side. mappedBy points at the field above.
@OneToMany(mappedBy = "doctor")
private List<Appointment> appointments = new ArrayList<>();
```

Read it as a sentence: *many appointments belong to one doctor; one doctor has
many appointments.* Only one table can physically hold the foreign key, and
that is `appointment.doctor_id`. `mappedBy = "doctor"` is how you tell
Hibernate "the other side already owns this - do not create a second column
over here."

Get `mappedBy` wrong and Hibernate quietly creates an extra join table. If you
ever see a table you did not design appear in SSMS, a missing `mappedBy` is the
first thing to check.

The payoff is that in Java you never write a join by hand:

```java
appointment.getDoctor().getUser().getFullName()   // "Dr. Ruwan Gunathilake"
```

Hibernate turns that into the SQL joins for you.

### One-to-one, briefly

`Patient` and `Doctor` each hold a `@OneToOne` to `User`. That is because every
actor who can log in has a row in `users`, and patients and doctors carry extra
fields (date of birth, specialization) that receptionists and pharmacists do
not. It is the "System User" generalisation from the use case diagram,
implemented as a table split.

### Helper methods are not columns

```java
public String getDateTimeLabel() {
    return DisplayFormat.dateTime(appointmentDateTime);
}
```

That is an ordinary Java method. Because `@Id` is on a *field*, Hibernate uses
**field access** and ignores extra getters entirely - it never tries to make a
`dateTimeLabel` column. These helpers exist so the templates stay free of
formatting logic.

---

## 3. Repository interfaces - the queries you never write

**File:** `repository/AppointmentRepository.java`

Here is the whole file's structure - and notice there is **no implementation
class anywhere in the project**:

```java
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    boolean existsByDoctorAndAppointmentDateTimeAndStatusNot(
            Doctor doctor, LocalDateTime appointmentDateTime, AppointmentStatus excludedStatus);

    List<Appointment> findByPatientOrderByAppointmentDateTimeDesc(Patient patient);

    long countByDoctorAndStatus(Doctor doctor, AppointmentStatus status);
}
```

**Spring writes the implementation at startup.** It creates a proxy object that
implements this interface, and injects that wherever an `AppointmentRepository`
is asked for. This is the single biggest "wait, where is the code?" moment for
people new to Spring - the answer is that there is no code, by design.

### What you get free from `JpaRepository<Appointment, Long>`

The two type parameters are *the entity* and *the type of its `@Id`*. Extending
it gives you, with no code at all:

| Method | Does |
|---|---|
| `save(appointment)` | INSERT if the id is null, UPDATE if it is not. Returns the saved object **with its new id filled in**. |
| `findById(5L)` | Returns `Optional<Appointment>` - empty rather than null if there is no row 5. |
| `findAll()` | Every row. |
| `deleteById(5L)` | DELETE. |
| `count()` | `SELECT COUNT(*)`. |
| `existsById(5L)` | Cheap existence check. |

### How a method name becomes SQL

This is the clever part. Spring parses the **name** of the method:

```java
boolean existsByDoctorAndAppointmentDateTimeAndStatusNot(
        Doctor doctor, LocalDateTime appointmentDateTime, AppointmentStatus excludedStatus);
```

Split it up:

| Piece | Meaning |
|---|---|
| `existsBy` | The query returns a boolean |
| `Doctor` | `WHERE doctor_id = ?` (first parameter) |
| `And` | join the conditions |
| `AppointmentDateTime` | `AND appointment_date_time = ?` (second parameter) |
| `And` | |
| `StatusNot` | `AND status <> ?` (third parameter) |

Result:

```sql
SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
FROM appointment
WHERE doctor_id = ? AND appointment_date_time = ? AND status <> ?
```

**The parameter names do not matter - the method name does, and the parameter
ORDER does.** The property names in the method name have to match the *Java
field names on the entity* (`appointmentDateTime`), not the column names
(`appointment_date_time`).

A simpler one from the same file:

```java
List<Appointment> findByPatientOrderByAppointmentDateTimeDesc(Patient patient);
//                     ^^^^^^^        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//                     WHERE patient_id = ?     ORDER BY appointment_date_time DESC
```

Other keywords this project uses: `Between`, `In`, `Containing`, `IgnoreCase`,
`IsNull`, `True`, `countBy`, `findAllBy`.

### When a method name would be ridiculous, write JPQL

Some things a method name cannot express. This one compares two columns of the
same row:

```java
@Query("select m from Medicine m where m.stockQuantity <= m.reorderThreshold order by m.name")
List<Medicine> findLowStockMedicines();
```

And this one reaches through two relationships, which as a method name would be
unreadable:

```java
@Query("select c from Consultation c where c.appointment.patient.id = :patientId "
        + "order by c.consultationDateTime desc")
List<Consultation> findPatientHistory(@Param("patientId") Long patientId);
```

Note this is **JPQL, not SQL**: `Consultation` is the *class* name and
`consultationDateTime` is the *field* name. You are querying objects; Hibernate
translates to real SQL. `:patientId` is a named parameter, bound by
`@Param("patientId")`.

That second query, by the way, is the `<<include>> View Patient Medical
History` from the use case diagram. A patient's history is simply every
consultation hanging off one of their appointments - which is why this project
has no separate `PatientRecord` table.

---

## 4. Service classes - where the rules live

**File:** `service/AppointmentService.java`

This is the most important class in the module, and the one to show a marker.

### Why the rules are here and not in the controller

Three reasons, in order of how convincing they are:

1. **Reuse.** `TelemedicineService.bookVideoConsultation()` calls
   `appointmentService.bookAppointment()`. Booking a video consultation
   automatically gets every rule - working hours, double-booking, past dates -
   without one line being copied. If those rules were in
   `AppointmentController`, Adithya's module would have had to duplicate them.
2. **Transactions.** `@Transactional` works on services. Multiple database
   writes inside one service method either all succeed or all roll back.
3. **Testability.** You can unit-test `bookAppointment` with no browser, no
   HTTP, no session.

### The booking rules, as actually written

```java
@Transactional
public Appointment bookAppointment(Patient patient, AppointmentForm form) {
    Doctor doctor = getDoctor(form.getDoctorId());
    String doctorName = doctor.getFullName();

    LocalTime time = parseSlot(form.getSlot());
    LocalDate date = form.getDate();
    LocalDateTime when = LocalDateTime.of(date, time);

    // (1) The slot must still be in the future.
    if (!when.isAfter(LocalDateTime.now())) {
        throw new BookingException("That date and time has already passed. Please choose a future slot.");
    }

    // (3) The doctor must work on that day of the week.
    if (!doctor.isAvailableOn(date.getDayOfWeek())) {
        throw new BookingException(doctorName + " does not consult on "
                + capitalise(date.getDayOfWeek().name()) + "s. Available days: "
                + doctor.getAvailableDaysLabel() + ".");
    }

    // (6) THE DOUBLE-BOOKING CHECK - the UC-01 "appointment conflict" extension.
    if (appointmentRepository.existsByDoctorAndAppointmentDateTimeAndStatusNot(
            doctor, when, AppointmentStatus.CANCELLED)) {
        throw new BookingException("That slot with " + doctorName
                + " has just been taken. Please choose another time.");
    }

    // (7) The patient cannot be in two places at once.
    if (appointmentRepository.existsByPatientAndAppointmentDateTimeAndStatusNot(
            patient, when, AppointmentStatus.CANCELLED)) {
        throw new BookingException("You already have another appointment at that time.");
    }

    Appointment appointment = new Appointment(patient, doctor, when, form.getType());
    appointment.setReason(form.getReason());
    appointment.setStatus(AppointmentStatus.PENDING);
    return appointmentRepository.save(appointment);
}
```

(Checks 2, 4 and 5 - not booking more than 60 days ahead, staying inside
working hours, landing on the half-hour grid - are in the real file; they were
trimmed here for space.)

**Every one of those `throw` statements is an extension flow from the UC-01
scenario.** That is the sentence to say in the viva. The use case document is
not decoration - each numbered alternate flow is a specific `if` in this method.

Notice the CANCELLED trick in check 6: we exclude cancelled appointments, so
when somebody cancels, their slot becomes bookable again. One enum constant
does the whole job.

### `@Transactional`

```java
@Transactional
public Appointment bookAppointment(...) { ... }

@Transactional(readOnly = true)
public List<Appointment> findForPatient(Patient patient) { ... }
```

`@Transactional` wraps the method in a database transaction: everything commits
together at the end, or if an exception escapes, **everything rolls back**.

The clearest example in this project is not booking but dispensing, in
`PharmacyService`:

```java
@Transactional
public Prescription dispense(Long prescriptionId, User pharmacist, String note) {
    ...
    // Step 1 - check the WHOLE prescription first.
    for (PrescriptionItem item : prescription.getItems()) {
        if (item.getMedicine().getStockQuantity() < item.getQuantity()) {
            throw new InsufficientStockException("Not enough stock of " + ...);
        }
    }

    // Step 2 - THE INCLUDED "Update Medicine Stock".
    for (PrescriptionItem item : prescription.getItems()) {
        Medicine medicine = item.getMedicine();
        medicine.setStockQuantity(medicine.getStockQuantity() - item.getQuantity());
        medicineRepository.save(medicine);
    }

    // Step 3 - stamp the prescription.
    prescription.setStatus(PrescriptionStatus.DISPENSED);
    ...
}
```

Without `@Transactional`, a prescription for three medicines that ran out on
the third one would have already deducted the first two. With it, the throw
rolls the whole thing back and the stock is untouched. That is also, literally,
the `<<include>>` relationship from the diagram: you cannot mark a prescription
dispensed without the stock moving, because both happen inside one method.

`readOnly = true` on the query methods is a hint that lets Hibernate skip
change-tracking. It is a small performance win and it documents intent.

---

## 5. Controller classes - the traffic police

**File:** `controller/AppointmentController.java`

```java
@Controller                       // Spring MVC: this class handles web requests
@RequestMapping("/patient")       // every URL below starts with /patient
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService, ...) {
        this.appointmentService = appointmentService;
    }
```

### The GET that shows the form

```java
@GetMapping("/appointments/book")
public String bookForm(@RequestParam(name = "doctorId", required = false) Long doctorId,
                       @RequestParam(name = "date", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       Model model) {

    AppointmentForm form = new AppointmentForm();
    form.setDoctorId(doctorId);
    form.setDate(date != null ? date : LocalDate.now().plusDays(1));

    model.addAttribute("appointmentForm", form);   // <- the template will use ${appointmentForm}
    prepareBookingModel(model, form);              // adds doctors, slots, minDate, maxDate
    return "patient/book-appointment";             // <- templates/patient/book-appointment.html
}
```

Three things to understand here:

**`@RequestParam`** pulls a value out of the query string. For the URL
`/patient/appointments/book?doctorId=2&date=2026-09-20`, `doctorId` becomes
`2L` and `date` becomes a `LocalDate`. `required = false` means the page also
works with no parameters at all. `@DateTimeFormat` tells Spring how to parse
the text `2026-09-20` into a `LocalDate`.

**`Model`** is a map that gets handed to the template. `model.addAttribute("doctors", list)`
in Java becomes `${doctors}` in the HTML. That is the whole contract between
controller and view.

**The return value is a view name, not HTML.** `"patient/book-appointment"`
means "render `src/main/resources/templates/patient/book-appointment.html`".
Spring adds the folder and the `.html` for you.

### The POST that does the work

```java
@PostMapping("/appointments/book")
public String book(@Valid @ModelAttribute("appointmentForm") AppointmentForm appointmentForm,
                   BindingResult binding,
                   HttpSession session,
                   Model model,
                   RedirectAttributes redirect) {

    if (binding.hasErrors()) {                        // (a) field-level validation failed
        prepareBookingModel(model, appointmentForm);
        return "patient/book-appointment";
    }
    try {
        Patient patient = currentPatient(session);
        Appointment appointment = appointmentService.bookAppointment(patient, appointmentForm);
        redirect.addFlashAttribute("success", "Appointment booked with "
                + appointment.getDoctor().getFullName() + " on " + appointment.getDateTimeLabel() + ".");
        return "redirect:/patient/appointments";       // (b) success
    } catch (BusinessException ex) {
        model.addAttribute("error", ex.getMessage());  // (c) a business rule said no
        prepareBookingModel(model, appointmentForm);
        return "patient/book-appointment";
    }
}
```

**`@ModelAttribute("appointmentForm")`** is the one that does the magic. Spring
creates an `AppointmentForm`, then for each form field posted, looks for a
matching setter and calls it: `doctorId=2` calls `setDoctorId(2L)`,
`slot=09:30` calls `setSlot("09:30")`. That is why the DTO needs getters and
setters for everything on the form. The name in the brackets must match the
`th:object` in the template.

**`@Valid`** runs the Bean Validation annotations on the DTO *before* the method
body executes. Results go into `BindingResult`.

> **The one rule you must not break:** `BindingResult` has to be the
> **immediately next parameter** after the `@Valid` object. Put `Model` between
> them and Spring throws an exception at startup instead of collecting the
> errors.

**`return "redirect:/patient/appointments"`** does not render anything - it
sends the browser a redirect. This is the **Post/Redirect/Get** pattern, and
the reason for it is practical: without it, a user pressing F5 after booking
re-submits the POST and books a second appointment.

**`RedirectAttributes.addFlashAttribute`** survives exactly one redirect. A
normal `model.addAttribute` would be thrown away by the redirect; a flash
attribute is stashed in the session, shown on the next page, then deleted. That
is how the green "Appointment booked..." banner reaches the appointments page.

### Notice what the controller does NOT do

No `if` about dates. No SQL. No stock arithmetic. If you ever find yourself
writing a business rule in a controller, it belongs one layer down.

---

## 6. Thymeleaf templates - the HTML

**File:** `templates/patient/book-appointment.html`

Thymeleaf files are ordinary HTML with extra `th:` attributes. You can open one
in a browser directly and it renders (with placeholder text) - that is
Thymeleaf's design goal.

Only four attributes really matter.

### `th:each` - loop

```html
<tr th:each="a : ${appointments}">
    <td th:text="${a.dateTimeLabel}">date</td>
    <td th:text="${a.doctor.fullName}">doctor</td>
</tr>
```

Read it as `for (Appointment a : appointments)`. One `<tr>` is produced per
item. `${a.doctor.fullName}` calls `a.getDoctor().getUser().getFullName()` -
Thymeleaf turns a property path into getter calls. The `date` and `doctor` text
inside the tags is dummy content, replaced at render time.

### `th:if` / `th:unless` - conditionals

```html
<div th:if="${slots.isEmpty()}" class="empty">
    No free slots for that doctor on that date.
</div>

<form th:unless="${slots.isEmpty()}" method="post"> ... </form>
```

`th:if` renders the element only when the condition is true; `th:unless` is its
opposite. This pair is how the "no slots available" alternate flow from UC-01
appears on screen.

### `th:object` and `th:field` - forms

```html
<form th:action="@{/patient/appointments/book}" th:object="${appointmentForm}" method="post">

    <input type="hidden" th:field="*{doctorId}">

    <select id="slot" th:field="*{slot}">
        <option th:each="s : ${slots}" th:value="${s}" th:text="${s}">09:00</option>
    </select>

    <button class="btn" type="submit">Confirm booking</button>
</form>
```

- `th:object="${appointmentForm}"` says "this form is bound to that object".
- `*{slot}` is shorthand for "the `slot` property **of that object**". The `*`
  saves repeating `${appointmentForm.slot}` on every field.
- **`th:field` does three jobs at once.** It generates `name="slot"`,
  `id="slot"`, and `value="..."` pre-filled from the object. That last one is
  why, when a booking is rejected, the form comes back with the user's choices
  still in it instead of blank.
- `@{/patient/appointments/book}` is a **URL expression**. Use it instead of a
  plain `href` so the link still works if the app is deployed under a context
  path.

### Fragments - the shared navbar

Every page starts the same way:

```html
<head th:replace="~{fragments/layout :: head('Book an appointment')}"></head>
<body>
<nav th:replace="~{fragments/layout :: navbar}"></nav>
```

`fragments/layout.html` defines them once:

```html
<head th:fragment="head(pageTitle)">
    <title th:text="'HealthConnect - ' + ${pageTitle}">HealthConnect</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
```

One navbar, one stylesheet, thirty-nine pages. Change the navbar once and every
screen updates.

The navbar knows who is logged in because of this, in
`config/GlobalControllerAdvice`:

```java
@ControllerAdvice
public class GlobalControllerAdvice {
    @ModelAttribute("currentUser")
    public SessionUser currentUser(HttpSession session) {
        return (SessionUser) session.getAttribute(AuthInterceptor.SESSION_KEY);
    }
}
```

`@ControllerAdvice` means "apply to every controller". So `${currentUser}` is
available in every template without a single controller adding it.

---

## 7. DTOs - why the form is not the entity

**File:** `dto/AppointmentForm.java`

DTO stands for Data Transfer Object. In this project it means: a small class
whose only job is to carry one form's fields between the browser and the
service.

The tempting shortcut is to bind the form straight to the `Appointment` entity.
We deliberately do not, for four reasons.

**1. The shapes genuinely differ.** The booking form collects a *date* and a
*time slot string* as two separate fields, because that is how the HTML date
picker and the slot dropdown work. The entity stores one `LocalDateTime`. The
service combines them:

```java
LocalTime time = parseSlot(form.getSlot());     // "09:30" -> LocalTime
LocalDateTime when = LocalDateTime.of(date, time);
```

The form also sends `doctorId` (a number from a dropdown), while the entity
needs an actual `Doctor` object, which the service looks up.

**2. Security.** If a form bound directly to the entity, anyone could add
`&status=COMPLETED` to the POST and Spring would happily set it. This is called
**mass assignment**. A DTO has only the fields the form is allowed to send,
so the attack has nothing to bind to. The clearest case is `RegistrationForm`,
which has `password` and `confirmPassword` - neither of which should ever exist
on the `User` entity, because only the *hash* is ever stored.

**3. Validation messages belong to the form, not the table.** `@NotNull(message
= "Please choose a doctor")` is about this screen. The entity should not carry
UI text.

**4. Hibernate would try to save it.** A half-filled entity bound to a form is
a managed object inside a transaction; a stray setter call can trigger an
UPDATE you never asked for. A DTO is an ordinary object Hibernate has never
heard of.

```java
public class AppointmentForm {

    @NotNull(message = "Please choose a doctor")
    private Long doctorId;                        // an id, not a Doctor

    @NotNull(message = "Please choose a date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;                       // split from the time

    @NotNull(message = "Please choose a time slot")
    private String slot;                          // "09:30"

    @Size(max = 255, message = "Reason is too long")
    private String reason;
    // + getters and setters for every field - Spring needs them to bind
}
```

The other DTOs in the project: `LoginForm`, `RegistrationForm`, `ProfileForm`,
`PasswordResetForm`, `ConsultationForm` (with a nested list of
`PrescriptionItemForm`), `FeedbackForm`, `MedicineForm`, `StaffForm`, plus
`SessionUser` (what goes in the session) and two report rows.

---

## 8. Validation and error handling

Errors reach the user through **two different routes**, and knowing which is
which is worth understanding.

### Route 1: field validation, with `@Valid` and `BindingResult`

For "you left this blank" or "that is too long" - things checkable by looking at
one field on its own.

Annotate the DTO:

```java
@NotNull(message = "Please choose a doctor")
private Long doctorId;
```

Ask for them to be checked in the controller:

```java
public String book(@Valid @ModelAttribute("appointmentForm") AppointmentForm appointmentForm,
                   BindingResult binding, ...) {
    if (binding.hasErrors()) {
        prepareBookingModel(model, appointmentForm);
        return "patient/book-appointment";       // re-show the form
    }
```

Show them in the template:

```html
<select id="slot" th:field="*{slot}"> ... </select>
<div class="field-error" th:if="${#fields.hasErrors('slot')}" th:errors="*{slot}"></div>
```

`th:errors` prints the `message` from the annotation, right under the field it
belongs to.

### Route 2: business rules, with exceptions

"The doctor is already booked at 09:30" cannot be checked by looking at one
field - it needs the database. That is a **business rule**, and business rules
live in the service and are reported by throwing.

The exception hierarchy is four tiny classes in `exception/`:

```
BusinessException  (extends RuntimeException)
 ├── BookingException            UC-01 / UC-04: conflict, past date, wrong day
 ├── InsufficientStockException  UC-03: not enough stock to dispense
 └── ResourceNotFoundException   a hand-typed id that does not exist
```

They extend `RuntimeException` so services do not need `throws` clauses on
every method.

The service throws:

```java
throw new BookingException("That slot with " + doctorName
        + " has just been taken. Please choose another time.");
```

The controller catches and re-shows the page:

```java
} catch (BusinessException ex) {
    model.addAttribute("error", ex.getMessage());
    prepareBookingModel(model, appointmentForm);
    return "patient/book-appointment";
}
```

Catching the **base class** means one `catch` handles all four subclasses.

The shared template fragment displays it:

```html
<div th:fragment="messages">
    <div class="alert success" th:if="${success}" th:text="${success}"></div>
    <div class="alert error"   th:if="${error}"   th:text="${error}"></div>
</div>
```

### The safety net

If a `BusinessException` is ever thrown somewhere a controller does not catch
it, `GlobalControllerAdvice` catches it application-wide:

```java
@ExceptionHandler(BusinessException.class)
public String handleBusinessException(BusinessException exception, Model model) {
    model.addAttribute("message", exception.getMessage());
    return "error/business";
}
```

The user sees a tidy page with the message instead of a Java stack trace.

### The third kind: access control

Not really an error - a request that never reaches a controller at all.
`config/AuthInterceptor` runs first:

```java
private static final Map<String, Set<Role>> RULES = new LinkedHashMap<>();
static {
    RULES.put("/patient",   EnumSet.of(Role.PATIENT));
    RULES.put("/doctor",    EnumSet.of(Role.DOCTOR));
    RULES.put("/pharmacy",  EnumSet.of(Role.PHARMACIST));
    RULES.put("/reception", EnumSet.of(Role.RECEPTIONIST, Role.ADMIN));
    RULES.put("/support",   EnumSet.of(Role.PATIENT_RELATIONS_OFFICER, Role.ADMIN));
    RULES.put("/admin",     EnumSet.of(Role.ADMIN));
}

@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    SessionUser currentUser = (SessionUser) request.getSession().getAttribute(SESSION_KEY);

    if (currentUser == null) {                                    // not logged in
        response.sendRedirect(request.getContextPath() + "/login?required");
        return false;                                             // stop the request
    }
    // ...then check the role against the prefix rules, and /denied if it does not match
    return true;
}
```

`preHandle` returning `false` stops the request dead. A patient typing
`/admin/staff` into the address bar never reaches `AdminController`.

There is a second, independent layer of protection inside the services, for
things a URL prefix cannot express. A patient *is* allowed on
`/patient/appointments/...`, so the interceptor cannot stop them cancelling
somebody else's booking by changing the id. The service does:

```java
if (patientIdOrNull != null && !appointment.getPatient().getId().equals(patientIdOrNull)) {
    throw new BusinessException("You can only cancel your own appointments.");
}
```

Two layers: the interceptor for "which role may see this screen", the service
for "which rows may this person touch".

---

## 9. How this maps to your module

Every module is the same five-class pattern. Find your row, and read
section 2-8 again with your own class names substituted in.

### Rathnayaka R.M.C.R.B. - Appointment Management (UC-01)

This *is* the worked example. Your classes: `Appointment`, `Patient`, `Doctor`,
`AppointmentRepository`, `AppointmentService`, `AppointmentController`
(patient side) and `ReceptionController` (staff side), templates under
`templates/patient/` and `templates/reception/`.

You also own **patient registration**, which lives in `AuthService.registerPatient()`
and `HomeController` - it creates the `users` row and the `patient` row together.

**Show in the demo:** book the same slot twice. The second attempt hits check
(6) in `bookAppointment` and you get a red message instead of a corrupt
schedule.

---

### Gunathilake H.R.N.V. - Doctor Consultation Management (UC-02)

| In the example | In your module |
|---|---|
| `Appointment` entity | **`Consultation`** entity (+ `Prescription`, `PrescriptionItem`) |
| `AppointmentRepository` | **`ConsultationRepository`** |
| `AppointmentService.bookAppointment()` | **`ConsultationService.recordConsultation()`** |
| `AppointmentController` | **`ConsultationController`** |
| `AppointmentForm` | **`ConsultationForm`** (+ nested `PrescriptionItemForm`) |
| `book-appointment.html` | **`doctor/consultation-form.html`** |

**Your special bit is the `<<include>>`.** `ConsultationRepository.findPatientHistory(patientId)`
is the "View Patient Medical History" step - a real JPQL query, shown on
`doctor/patient-history.html` and also right beside the notes box on the
consultation form.

**Your second special bit** is that one form saves two things. `recordConsultation`
writes the `Consultation`, builds a `Prescription` with its items, and flips the
appointment to `COMPLETED` - all inside one `@Transactional` method. Your form
uses indexed binding, `*{items[0].medicineId}`, which is how five medicine rows
come back as a `List<PrescriptionItemForm>`.

---

### Ahamed M.N.N. - Pharmacy & Prescription Management (UC-03)

| In the example | In your module |
|---|---|
| `Appointment` entity | **`Medicine`** entity (and you read `Prescription`) |
| `AppointmentRepository` | **`MedicineRepository`**, `PrescriptionRepository` |
| `AppointmentService.bookAppointment()` | **`PharmacyService.dispense()`** |
| `AppointmentController` | **`PharmacyController`** |
| `AppointmentForm` | **`MedicineForm`** |
| the double-booking check | **the insufficient-stock check** |

**Your class is the best `@Transactional` example in the project** - use it.
`dispense()` checks every line first, then deducts, then stamps. If line three
is short, lines one and two are rolled back. That is the
`<<include>> Update Medicine Stock` made real: dispensing cannot happen without
the stock moving, because they are the same transaction.

Your low-stock alert is the hand-written JPQL in `MedicineRepository`, because
comparing `stockQuantity <= reorderThreshold` is two columns of one row -
something a derived method name cannot express.

---

### Adithya J.M.O. - Telemedicine / Online Consultation (UC-04)

| In the example | In your module |
|---|---|
| `Appointment` entity | **`VideoSession`** entity (one-to-one with `Appointment`) |
| `AppointmentRepository` | **`VideoSessionRepository`** |
| `AppointmentService` | **`TelemedicineService`** |
| `AppointmentController` | **`TelemedicineController`** |
| `AppointmentForm` | **the same `AppointmentForm`**, with `type = VIDEO` |

**Your talking point is that your service is deliberately tiny**, and that is
the design working, not a gap:

```java
@Transactional
public VideoSession bookVideoConsultation(Patient patient, AppointmentForm form) {
    form.setType(AppointmentType.VIDEO);

    // Every UC-01 validation rule applies unchanged.
    Appointment appointment = appointmentService.bookAppointment(patient, form);

    VideoSession session = new VideoSession(appointment, generateMeetingLink());
    session.setStatus(SessionStatus.SCHEDULED);
    return videoSessionRepository.save(session);
}
```

That single call to `appointmentService.bookAppointment()` is the use case
diagram's generalisation - "Conduct In-Person Consultation" and "Conduct Video
Consultation" both generalise to "Conduct Consultation" - written in Java. You
reuse the whole booking model instead of building a parallel one.

**Be upfront about the scope boundary:** live video calling is out of scope, so
a `VideoSession` is a scheduled record with a placeholder meeting link and a
Scheduled -> In Progress -> Completed lifecycle. Say it before anyone asks.

---

### Perera M.K.S.N. - Patient Feedback & Support Management (UC-05)

| In the example | In your module |
|---|---|
| `Appointment` entity | **`Feedback`** entity |
| `AppointmentRepository` | **`FeedbackRepository`** |
| `AppointmentService.bookAppointment()` | **`FeedbackService.submitFeedback()`** |
| `AppointmentController` | **`FeedbackController`** |
| `AppointmentForm` | **`FeedbackForm`** |
| the double-booking check | **the "one rating per visit" check** |

`submitFeedback` has three guards worth pointing at, all of them rules from
your use case: the visit must belong to this patient, it must be `COMPLETED`,
and `existsByAppointment` stops a second rating for the same visit.

**Your special bit is the cross-module `<<include>>`.** One `feedback` table
serves four screens: the patient submits it, the doctor sees their own ratings,
the Patient Relations Officer answers it, and the administrator's report
aggregates it. Nothing is duplicated - "View Patient Feedback Reports includes
View Feedback and Ratings" is implemented as shared data.

Your average-rating query is the JPQL one-liner returning `Double` (the object,
not `double`) - because a doctor with no feedback yet gives `null`, not zero.

---

### Vishalan S. - Hospital Administration & Reporting (UC-06)

| In the example | In your module |
|---|---|
| `Appointment` entity | **`User`** (you manage the accounts themselves) |
| `AppointmentRepository` | **`UserRepository`**, `DoctorRepository` |
| `AppointmentService.bookAppointment()` | **`AdminService.saveStaff()`** |
| - | **`ReportService`** (no equivalent - it is yours alone) |
| `AppointmentController` | **`AdminController`** |
| `AppointmentForm` | **`StaffForm`** |

**Your special bit is that creating a doctor creates two rows** - the login in
`users` and the profile in `doctor` - inside one `@Transactional` method.
`saveStaff` also enforces that only the four staff roles can be created here,
that emails stay unique, and that an existing account's role cannot change.

**Your second special bit is reporting.** `ReportService` builds
`DoctorAppointmentRow` and `DoctorRatingRow` by counting real rows. Be ready
for "why a loop instead of `GROUP BY`?" - the honest answer is that at this data
size the loop is clearer to read and defend, and it is a deliberate trade-off,
not an oversight.

You also own the access-control story, because `AuthInterceptor` is the central
role management the brief asked for. Be able to explain the `RULES` map and
`preHandle`.

---

## 10. Questions you might get asked

**"Where is the SQL?"**
There almost isn't any. Hibernate generates it from the `@Entity` annotations
and the repository method names. The only hand-written queries are the handful
of `@Query` JPQL strings, and even those query *objects*, not tables. Turn on
`spring.jpa.show-sql=true` (it already is) and the generated SQL prints in the
console - a good thing to show live.

**"Why didn't you use Spring Security?"**
Because the group can explain this. The logged-in user is a `SessionUser` in
the `HttpSession`; `AuthInterceptor` maps URL prefixes to allowed roles. It is
about twenty lines of plain Java with no framework magic. Passwords are still
BCrypt-hashed via `spring-security-crypto` - we use the crypto library without
the framework. In a production system with SSO, password-reset tokens and CSRF
protection, Spring Security would be the right answer, and that trade-off is
the honest thing to say.

**"How are passwords stored?"**
BCrypt hashes, never plain text. `AuthService` calls
`passwordEncoder.encode(...)` on registration and
`passwordEncoder.matches(typed, stored)` on login. BCrypt is one-way - even we
cannot read a user's password. Run
`SELECT email, password_hash FROM users;` in SSMS to show it.

**"What happens if two patients book the same slot at the same instant?"**
`existsBy...` then `save` is a check-then-act, so in theory two simultaneous
requests could both pass the check. For this project's scale that is
acceptable, and the honest production answer is a unique database constraint on
`(doctor_id, appointment_date_time)` so the second insert fails at the database
level. Knowing the limit of your own design scores better than pretending it
isn't there.

**"Show me an alternate flow from your use case actually working."**
Best three: book the same slot twice (UC-01 conflict); try to dispense the
seeded Amoxicillin prescription when stock is short (UC-03); log in as a
patient and type `/admin/staff` in the address bar (role-based access).

**"What is `@Transactional` for?"**
All-or-nothing. `PharmacyService.dispense()` is the example: check all lines,
deduct all lines, stamp the prescription - if anything throws, every change
rolls back and the stock is untouched.

**"Why is `Appointment` one table when you have two kinds of consultation?"**
Because the use case diagram says "Conduct In-Person Consultation" and "Conduct
Video Consultation" both generalise to "Conduct Consultation". One table plus a
`type` column expresses that generalisation; two parallel tables would have
meant duplicating every booking rule.

---

*That is the whole system. Five classes per module, always in the same order:
entity, repository, service, controller, template. If you can explain the
appointment booking flow, you can explain yours.*
