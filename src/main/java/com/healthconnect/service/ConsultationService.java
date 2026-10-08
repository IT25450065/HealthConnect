package com.healthconnect.service;

import com.healthconnect.dto.ConsultationForm;
import com.healthconnect.dto.PrescriptionItemForm;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.exception.ResourceNotFoundException;
import com.healthconnect.model.Appointment;
import com.healthconnect.model.AppointmentStatus;
import com.healthconnect.model.Consultation;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Medicine;
import com.healthconnect.model.Patient;
import com.healthconnect.model.Prescription;
import com.healthconnect.model.PrescriptionItem;
import com.healthconnect.model.SessionStatus;
import com.healthconnect.repository.AppointmentRepository;
import com.healthconnect.repository.ConsultationRepository;
import com.healthconnect.repository.MedicineRepository;
import com.healthconnect.repository.PatientRepository;
import com.healthconnect.repository.PrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DOCTOR CONSULTATION MANAGEMENT MODULE - Gunathilake H.R.N.V. -
 * UC-02 Record Consultation Notes and Create Digital Prescription.
 *
 * Two things happen here that the use case diagram calls out explicitly:
 *
 *  - &lt;&lt;include&gt;&gt; View Patient Medical History: viewPatientHistory()
 *    below is a real query over past consultations, not a placeholder screen.
 *
 *  - The digital prescription is created in the SAME transaction as the notes,
 *    and is structured (medicine, dosage, instructions, quantity) precisely so
 *    that the Pharmacy module can consume it in UC-03.
 */
@Service
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicineRepository medicineRepository;
    private final PatientRepository patientRepository;

    public ConsultationService(ConsultationRepository consultationRepository,
                               AppointmentRepository appointmentRepository,
                               PrescriptionRepository prescriptionRepository,
                               MedicineRepository medicineRepository,
                               PatientRepository patientRepository) {
        this.consultationRepository = consultationRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.medicineRepository = medicineRepository;
        this.patientRepository = patientRepository;
    }

    /** Whose history are we looking at? Used by the patient-history screen. */
    @Transactional(readOnly = true)
    public Patient getPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found (id " + patientId + ")"));
    }

    /**
     * THE &lt;&lt;include&gt;&gt; "View Patient Medical History".
     *
     * A patient's history is every consultation attached to one of that
     * patient's past appointments, newest first. Each consultation carries its
     * own prescriptions, so one call gives the doctor visits + notes +
     * prescriptions.
     */
    @Transactional(readOnly = true)
    public List<Consultation> viewPatientHistory(Long patientId) {
        return consultationRepository.findPatientHistory(patientId);
    }

    @Transactional(readOnly = true)
    public List<Consultation> findByDoctor(Doctor doctor) {
        return consultationRepository.findByDoctor(doctor);
    }

    @Transactional(readOnly = true)
    public Consultation getConsultation(Long id) {
        return consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation not found (id " + id + ")"));
    }

    @Transactional(readOnly = true)
    public Consultation getByAppointment(Appointment appointment) {
        return consultationRepository.findByAppointment(appointment).orElse(null);
    }

    /**
     * UC-02 main flow.
     *
     * Guards first (is this the right doctor? has this visit already been
     * recorded?), then build the Consultation, attach one Prescription holding
     * every filled-in medicine line, and mark the appointment COMPLETED.
     *
     * Because the whole method is @Transactional, either all of that is saved
     * or none of it is - the database can never end up with consultation notes
     * whose prescription failed to save.
     */
    @Transactional
    public Consultation recordConsultation(Doctor doctor, ConsultationForm form) {
        Appointment appointment = appointmentRepository.findById(form.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found (id " + form.getAppointmentId() + ")"));

        if (!appointment.getDoctor().getId().equals(doctor.getId())) {
            throw new BusinessException("This appointment belongs to another doctor.");
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException("This appointment was cancelled and cannot be consulted.");
        }
        if (consultationRepository.existsByAppointment(appointment)) {
            throw new BusinessException("Consultation notes have already been recorded for this appointment.");
        }

        Consultation consultation = new Consultation(appointment);
        consultation.setSymptoms(form.getSymptoms());
        consultation.setDiagnosis(form.getDiagnosis());
        consultation.setNotes(form.getNotes());
        consultationRepository.save(consultation);

        Prescription prescription = buildPrescription(consultation, form.getItems());
        if (prescription != null) {
            prescriptionRepository.save(prescription);
            consultation.getPrescriptions().add(prescription);
        }

        // The visit is now done.
        appointment.setStatus(AppointmentStatus.COMPLETED);
        if (appointment.getVideoSession() != null) {
            appointment.getVideoSession().setStatus(SessionStatus.COMPLETED);
        }
        appointmentRepository.save(appointment);

        return consultation;
    }

    /**
     * Turns the filled-in rows of the form into one Prescription with its
     * items. Returns null when the doctor prescribed nothing, which is a
     * perfectly normal outcome (advice-only visit).
     */
    private Prescription buildPrescription(Consultation consultation, List<PrescriptionItemForm> itemForms) {
        Prescription prescription = new Prescription(consultation);
        boolean any = false;

        for (PrescriptionItemForm itemForm : itemForms) {
            if (!itemForm.isFilled()) {
                continue; // blank row - the doctor did not use this line
            }

            Medicine medicine = medicineRepository.findById(itemForm.getMedicineId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Medicine not found (id " + itemForm.getMedicineId() + ")"));

            int quantity = itemForm.getQuantity() == null ? 0 : itemForm.getQuantity();
            if (quantity <= 0) {
                throw new BusinessException("Enter a quantity of at least 1 for " + medicine.getName() + ".");
            }
            String dosage = itemForm.getDosage();
            if (dosage == null || dosage.isBlank()) {
                throw new BusinessException("Enter the dosage for " + medicine.getName() + ".");
            }

            prescription.addItem(new PrescriptionItem(medicine, dosage.trim(), itemForm.getInstructions(), quantity));
            any = true;
        }

        return any ? prescription : null;
    }
}
