package com.healthconnect.service;

import com.healthconnect.dto.DoctorAppointmentRow;
import com.healthconnect.dto.DoctorRatingRow;
import com.healthconnect.model.AppointmentStatus;
import com.healthconnect.model.AppointmentType;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.PrescriptionStatus;
import com.healthconnect.model.Role;
import com.healthconnect.repository.AppointmentRepository;
import com.healthconnect.repository.DoctorRepository;
import com.healthconnect.repository.FeedbackRepository;
import com.healthconnect.repository.MedicineRepository;
import com.healthconnect.repository.PatientRepository;
import com.healthconnect.repository.PrescriptionRepository;
import com.healthconnect.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * REPORTING half of the Hospital Administration and Reporting Management
 * module - Vishalan S. (UC-06).
 *
 * Every number on the report screens is counted from the real tables at the
 * moment the page is requested. Nothing here is hard-coded.
 */
@Service
public class ReportService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final FeedbackRepository feedbackRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicineRepository medicineRepository;
    private final UserRepository userRepository;

    public ReportService(AppointmentRepository appointmentRepository,
                         DoctorRepository doctorRepository,
                         PatientRepository patientRepository,
                         FeedbackRepository feedbackRepository,
                         PrescriptionRepository prescriptionRepository,
                         MedicineRepository medicineRepository,
                         UserRepository userRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.feedbackRepository = feedbackRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.medicineRepository = medicineRepository;
        this.userRepository = userRepository;
    }

    /**
     * APPOINTMENTS SUMMARY: one row per doctor with a breakdown by status.
     *
     * This loops over the doctors and asks the database for each count. With a
     * hospital-sized dataset you would write a single GROUP BY query instead,
     * but for this project the loop is clearer to read and to defend.
     */
    @Transactional(readOnly = true)
    public List<DoctorAppointmentRow> appointmentsByDoctor() {
        List<DoctorAppointmentRow> rows = new ArrayList<>();
        for (Doctor doctor : doctorRepository.findAllOrderByName()) {
            rows.add(new DoctorAppointmentRow(
                    doctor.getFullName(),
                    doctor.getSpecialization(),
                    appointmentRepository.countByDoctor(doctor),
                    appointmentRepository.countByDoctorAndStatus(doctor, AppointmentStatus.PENDING),
                    appointmentRepository.countByDoctorAndStatus(doctor, AppointmentStatus.CONFIRMED),
                    appointmentRepository.countByDoctorAndStatus(doctor, AppointmentStatus.COMPLETED),
                    appointmentRepository.countByDoctorAndStatus(doctor, AppointmentStatus.CANCELLED)));
        }
        return rows;
    }

    /** FEEDBACK AND RATINGS SUMMARY: one row per doctor. */
    @Transactional(readOnly = true)
    public List<DoctorRatingRow> ratingsByDoctor() {
        List<DoctorRatingRow> rows = new ArrayList<>();
        for (Doctor doctor : doctorRepository.findAllOrderByName()) {
            rows.add(new DoctorRatingRow(
                    doctor.getId(),
                    doctor.getFullName(),
                    doctor.getSpecialization(),
                    feedbackRepository.countByDoctor(doctor),
                    feedbackRepository.findAverageRatingByDoctor(doctor)));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public long countAppointments() {
        return appointmentRepository.count();
    }

    @Transactional(readOnly = true)
    public long countAppointments(AppointmentStatus status) {
        return appointmentRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countAppointments(AppointmentType type) {
        return appointmentRepository.countByType(type);
    }

    @Transactional(readOnly = true)
    public long countPatients() {
        return patientRepository.count();
    }

    @Transactional(readOnly = true)
    public long countDoctors() {
        return doctorRepository.count();
    }

    @Transactional(readOnly = true)
    public long countStaff() {
        return userRepository.findByRoleInOrderByFullNameAsc(AdminService.STAFF_ROLES).size();
    }

    @Transactional(readOnly = true)
    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    @Transactional(readOnly = true)
    public long countFeedback() {
        return feedbackRepository.count();
    }

    /** Null when nobody has left feedback yet. */
    @Transactional(readOnly = true)
    public Double overallAverageRating() {
        return feedbackRepository.findOverallAverageRating();
    }

    @Transactional(readOnly = true)
    public String overallAverageLabel() {
        Double average = overallAverageRating();
        return average == null ? "-" : String.format("%.2f", average);
    }

    @Transactional(readOnly = true)
    public long countPrescriptions(PrescriptionStatus status) {
        return prescriptionRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countMedicines() {
        return medicineRepository.count();
    }

    @Transactional(readOnly = true)
    public long countLowStockMedicines() {
        return medicineRepository.findLowStockMedicines().size();
    }
}
