package com.healthconnect.config;

import com.healthconnect.model.Appointment;
import com.healthconnect.model.AppointmentStatus;
import com.healthconnect.model.AppointmentType;
import com.healthconnect.model.Consultation;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Feedback;
import com.healthconnect.model.Medicine;
import com.healthconnect.model.Patient;
import com.healthconnect.model.Prescription;
import com.healthconnect.model.PrescriptionItem;
import com.healthconnect.model.PrescriptionStatus;
import com.healthconnect.model.Role;
import com.healthconnect.model.SessionStatus;
import com.healthconnect.model.User;
import com.healthconnect.model.VideoSession;
import com.healthconnect.repository.AppointmentRepository;
import com.healthconnect.repository.ConsultationRepository;
import com.healthconnect.repository.DoctorRepository;
import com.healthconnect.repository.FeedbackRepository;
import com.healthconnect.repository.MedicineRepository;
import com.healthconnect.repository.PatientRepository;
import com.healthconnect.repository.PrescriptionRepository;
import com.healthconnect.repository.UserRepository;
import com.healthconnect.repository.VideoSessionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Seeds demo data the first time the application starts against an empty
 * database, so the system can be demonstrated immediately after the DB is set
 * up. If the users table already has rows, this does nothing.
 *
 * Why a CommandLineRunner instead of data.sql? Because passwords must be
 * BCrypt-hashed, and that has to happen in Java at runtime - you cannot write
 * a valid BCrypt hash by hand in a SQL script.
 *
 * The seeder writes through the REPOSITORIES, not the services, on purpose:
 * it needs to create appointments in the past (for a realistic medical
 * history), which AppointmentService would correctly refuse to do.
 *
 * DEMO LOGIN - every seeded account uses the password:  health123
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final String DEMO_PASSWORD = "health123";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicineRepository medicineRepository;
    private final FeedbackRepository feedbackRepository;
    private final VideoSessionRepository videoSessionRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      PatientRepository patientRepository,
                      DoctorRepository doctorRepository,
                      AppointmentRepository appointmentRepository,
                      ConsultationRepository consultationRepository,
                      PrescriptionRepository prescriptionRepository,
                      MedicineRepository medicineRepository,
                      FeedbackRepository feedbackRepository,
                      VideoSessionRepository videoSessionRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.consultationRepository = consultationRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.medicineRepository = medicineRepository;
        this.feedbackRepository = feedbackRepository;
        this.videoSessionRepository = videoSessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            System.out.println("[HealthConnect] Database already contains data - seeding skipped.");
            return;
        }
        System.out.println("[HealthConnect] Empty database detected - inserting demo data...");

        // ---------------- staff accounts ----------------
        createUser("Mr. Vishalan S.", "admin@healthconnect.lk", Role.ADMIN, "0112345000");
        createUser("Ms. Nethmi Rathnayaka", "reception@healthconnect.lk", Role.RECEPTIONIST, "0112345001");
        User pharmacist = createUser("Mr. Nuwan Ahamed", "pharmacy@healthconnect.lk", Role.PHARMACIST, "0112345002");
        createUser("Ms. Sanduni Perera", "support@healthconnect.lk", Role.PATIENT_RELATIONS_OFFICER, "0112345003");

        // ---------------- doctors ----------------
        Doctor cardiologist = createDoctor("Dr. Ruwan Gunathilake", "cardiology@healthconnect.lk",
                "Cardiology", "MBBS, MD (Cardiology)", "C-102",
                "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY", LocalTime.of(9, 0), LocalTime.of(13, 0));
        Doctor generalPhysician = createDoctor("Dr. Hasini Adithya", "general@healthconnect.lk",
                "General Medicine", "MBBS", "G-201",
                "MONDAY,WEDNESDAY,FRIDAY,SATURDAY", LocalTime.of(8, 0), LocalTime.of(16, 0));
        Doctor dermatologist = createDoctor("Dr. Kavindu Fernando", "derma@healthconnect.lk",
                "Dermatology", "MBBS, MD (Dermatology)", "D-305",
                "TUESDAY,THURSDAY", LocalTime.of(14, 0), LocalTime.of(18, 0));

        // ---------------- patients ----------------
        Patient amal = createPatient("Amal Silva", "amal@example.com", "0771234567",
                LocalDate.of(1996, 4, 12), "Male", "24/3 Galle Road, Colombo 03", "O+");
        Patient nimali = createPatient("Nimali Jayawardena", "nimali@example.com", "0779876543",
                LocalDate.of(2001, 11, 2), "Female", "88 Kandy Road, Kadawatha", "A-");
        createPatient("Suresh Kumar", "suresh@example.com", "0715558888",
                LocalDate.of(1984, 7, 25), "Male", "12 Temple Lane, Jaffna", "B+");

        // ---------------- medicine inventory ----------------
        Medicine paracetamol = saveMedicine("Paracetamol", "500mg", "tablets", 480, 100);
        Medicine amoxicillin = saveMedicine("Amoxicillin", "250mg", "capsules", 60, 80);   // low stock
        Medicine atorvastatin = saveMedicine("Atorvastatin", "20mg", "tablets", 240, 50);
        saveMedicine("Cetirizine", "10mg", "tablets", 320, 60);
        saveMedicine("Omeprazole", "20mg", "capsules", 150, 40);
        saveMedicine("Metformin", "500mg", "tablets", 35, 50);                              // low stock
        saveMedicine("Salbutamol Inhaler", "100mcg", "inhalers", 42, 15);
        saveMedicine("Hydrocortisone Cream", "1%", "tubes", 25, 20);

        // ---------------- a completed visit, with notes + a dispensed prescription ----------------
        Appointment pastVisit = saveAppointment(amal, cardiologist,
                LocalDateTime.now().minusDays(21).withHour(10).withMinute(0).withSecond(0).withNano(0),
                AppointmentType.IN_PERSON, AppointmentStatus.COMPLETED, "Chest tightness when climbing stairs");

        Consultation pastConsultation = new Consultation(pastVisit);
        pastConsultation.setConsultationDateTime(pastVisit.getAppointmentDateTime().plusMinutes(10));
        pastConsultation.setSymptoms("Exertional chest tightness for three weeks. No syncope.");
        pastConsultation.setDiagnosis("Stable angina - mild. Raised LDL cholesterol.");
        pastConsultation.setNotes("ECG normal at rest. Advised statin therapy, low-fat diet and a review in one month.");
        consultationRepository.save(pastConsultation);

        Prescription dispensed = new Prescription(pastConsultation);
        dispensed.setIssuedAt(pastConsultation.getConsultationDateTime());
        dispensed.addItem(new PrescriptionItem(atorvastatin, "20mg - once at night", "After dinner", 30));
        dispensed.addItem(new PrescriptionItem(paracetamol, "500mg - as needed", "Maximum 4 per day", 20));
        dispensed.setStatus(PrescriptionStatus.DISPENSED);
        dispensed.setDispensedAt(pastConsultation.getConsultationDateTime().plusHours(1));
        dispensed.setDispensedBy(pharmacist);
        dispensed.setPharmacistNote("Counselled on statin timing.");
        prescriptionRepository.save(dispensed);

        // ---------------- a second completed visit, prescription still PENDING for the pharmacy demo ----------------
        Appointment recentVisit = saveAppointment(nimali, generalPhysician,
                LocalDateTime.now().minusDays(2).withHour(9).withMinute(30).withSecond(0).withNano(0),
                AppointmentType.IN_PERSON, AppointmentStatus.COMPLETED, "Sore throat and fever");

        Consultation recentConsultation = new Consultation(recentVisit);
        recentConsultation.setConsultationDateTime(recentVisit.getAppointmentDateTime().plusMinutes(8));
        recentConsultation.setSymptoms("Fever 38.6C for two days, sore throat, mild cough.");
        recentConsultation.setDiagnosis("Acute bacterial pharyngitis.");
        recentConsultation.setNotes("Throat swab not required. Advised rest and fluids. Review if no improvement in 3 days.");
        consultationRepository.save(recentConsultation);

        Prescription pending = new Prescription(recentConsultation);
        pending.setIssuedAt(recentConsultation.getConsultationDateTime());
        // Amoxicillin stock is deliberately low so the insufficient-stock path can be demonstrated.
        pending.addItem(new PrescriptionItem(amoxicillin, "250mg - three times daily", "After meals, 5 days", 15));
        pending.addItem(new PrescriptionItem(paracetamol, "500mg - three times daily", "After meals", 15));
        pending.setStatus(PrescriptionStatus.PENDING);
        prescriptionRepository.save(pending);

        // ---------------- upcoming appointments ----------------
        saveAppointment(amal, generalPhysician,
                nextAvailable(generalPhysician, LocalTime.of(9, 0)),
                AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED, "Annual check-up");

        saveAppointment(nimali, dermatologist,
                nextAvailable(dermatologist, LocalTime.of(14, 30)),
                AppointmentType.IN_PERSON, AppointmentStatus.PENDING, "Persistent rash on forearm");

        // ---------------- an upcoming VIDEO consultation (UC-04) ----------------
        Appointment videoVisit = saveAppointment(amal, cardiologist,
                nextAvailable(cardiologist, LocalTime.of(11, 30)),
                AppointmentType.VIDEO, AppointmentStatus.CONFIRMED, "Follow-up on statin therapy");

        VideoSession session = new VideoSession(videoVisit, "https://meet.healthconnect.local/session/a1b2c3d4");
        session.setStatus(SessionStatus.SCHEDULED);
        session.setPlatformNote("Placeholder link - live video calling is out of scope for this project.");
        videoSessionRepository.save(session);

        // ---------------- feedback (UC-05) ----------------
        Feedback happy = new Feedback(amal, cardiologist, pastVisit, 5,
                "Dr. Gunathilake explained everything very clearly and did not rush the consultation.");
        happy.setSubmittedAt(pastVisit.getAppointmentDateTime().plusDays(1));
        feedbackRepository.save(happy);

        Feedback mixed = new Feedback(nimali, generalPhysician, recentVisit, 3,
                "The consultation itself was fine but I waited almost an hour past my appointment time.");
        mixed.setSubmittedAt(recentVisit.getAppointmentDateTime().plusHours(4));
        feedbackRepository.save(mixed);

        System.out.println("[HealthConnect] Demo data inserted. Every account's password is: " + DEMO_PASSWORD);
        System.out.println("[HealthConnect]   admin@healthconnect.lk        (Hospital Administrator)");
        System.out.println("[HealthConnect]   cardiology@healthconnect.lk   (Doctor)");
        System.out.println("[HealthConnect]   reception@healthconnect.lk    (Receptionist)");
        System.out.println("[HealthConnect]   pharmacy@healthconnect.lk     (Pharmacist)");
        System.out.println("[HealthConnect]   support@healthconnect.lk      (Patient Relations Officer)");
        System.out.println("[HealthConnect]   amal@example.com              (Patient)");
    }

    // ------------------------------------------------------------------
    // small helpers
    // ------------------------------------------------------------------

    private User createUser(String fullName, String email, Role role, String phone) {
        User user = new User(fullName, email, passwordEncoder.encode(DEMO_PASSWORD), role);
        user.setPhone(phone);
        return userRepository.save(user);
    }

    private Doctor createDoctor(String fullName, String email, String specialization, String qualification,
                                String roomNo, String availableDays, LocalTime from, LocalTime to) {
        User user = createUser(fullName, email, Role.DOCTOR, "0112345100");
        Doctor doctor = new Doctor(user, specialization);
        doctor.setQualification(qualification);
        doctor.setRoomNo(roomNo);
        doctor.setAvailableDays(availableDays);
        doctor.setAvailableFrom(from);
        doctor.setAvailableTo(to);
        return doctorRepository.save(doctor);
    }

    private Patient createPatient(String fullName, String email, String phone,
                                  LocalDate dateOfBirth, String gender, String address, String bloodGroup) {
        User user = createUser(fullName, email, Role.PATIENT, phone);
        Patient patient = new Patient(user);
        patient.setDateOfBirth(dateOfBirth);
        patient.setGender(gender);
        patient.setAddress(address);
        patient.setBloodGroup(bloodGroup);
        return patientRepository.save(patient);
    }

    private Medicine saveMedicine(String name, String strength, String unit, int stock, int threshold) {
        return medicineRepository.save(new Medicine(name, strength, unit, stock, threshold));
    }

    private Appointment saveAppointment(Patient patient, Doctor doctor, LocalDateTime when,
                                        AppointmentType type, AppointmentStatus status, String reason) {
        Appointment appointment = new Appointment(patient, doctor, when, type);
        appointment.setStatus(status);
        appointment.setReason(reason);
        return appointmentRepository.save(appointment);
    }

    /**
     * The next date (starting tomorrow) that this doctor actually works,
     * at the given time - so the seeded future appointments are consistent
     * with each doctor's availability.
     */
    private LocalDateTime nextAvailable(Doctor doctor, LocalTime time) {
        LocalDate date = LocalDate.now().plusDays(1);
        for (int i = 0; i < 14; i++) {
            DayOfWeek day = date.getDayOfWeek();
            if (doctor.isAvailableOn(day)) {
                return LocalDateTime.of(date, time);
            }
            date = date.plusDays(1);
        }
        return LocalDateTime.of(LocalDate.now().plusDays(1), time);
    }
}
