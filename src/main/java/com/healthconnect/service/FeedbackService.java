package com.healthconnect.service;

import com.healthconnect.dto.FeedbackForm;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.exception.ResourceNotFoundException;
import com.healthconnect.model.Appointment;
import com.healthconnect.model.AppointmentStatus;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Feedback;
import com.healthconnect.model.Patient;
import com.healthconnect.model.User;
import com.healthconnect.repository.AppointmentRepository;
import com.healthconnect.repository.FeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PATIENT FEEDBACK AND SUPPORT MANAGEMENT MODULE - Perera M.K.S.N. -
 * UC-05 View Patient Feedback and Ratings.
 *
 * One Feedback table serves four different screens: the patient submits,
 * the doctor sees their own ratings, the Patient Relations Officer answers
 * complaints, and the administrator's feedback report aggregates the same
 * rows. That shared data IS the cross-cluster &lt;&lt;include&gt;&gt; from the
 * use case diagram - nothing is duplicated.
 */
@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final AppointmentRepository appointmentRepository;

    public FeedbackService(FeedbackRepository feedbackRepository, AppointmentRepository appointmentRepository) {
        this.feedbackRepository = feedbackRepository;
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * UC-05 main flow (patient side).
     *
     * Feedback is only meaningful after a visit actually happened, so three
     * rules apply: the visit must belong to this patient, it must be
     * COMPLETED, and it may only be rated once.
     */
    @Transactional
    public Feedback submitFeedback(Patient patient, FeedbackForm form) {
        Appointment appointment = appointmentRepository.findById(form.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found (id " + form.getAppointmentId() + ")"));

        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw new BusinessException("You can only leave feedback for your own visits.");
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new BusinessException("You can only leave feedback after the consultation is completed.");
        }
        if (feedbackRepository.existsByAppointment(appointment)) {
            throw new BusinessException("You have already left feedback for this visit.");
        }

        int rating = form.getRating() == null ? 0 : form.getRating();
        if (rating < 1 || rating > 5) {
            throw new BusinessException("Rating must be between 1 and 5.");
        }

        Feedback feedback = new Feedback(patient, appointment.getDoctor(), appointment, rating, form.getComments());
        feedback.setSubmittedAt(LocalDateTime.now());
        return feedbackRepository.save(feedback);
    }

    /** Visits this patient has completed but not yet rated - fills the dropdown. */
    @Transactional(readOnly = true)
    public List<Appointment> findRateableVisits(Patient patient) {
        List<Appointment> completed = new ArrayList<>(appointmentRepository
                .findByPatientAndStatusOrderByAppointmentDateTimeDesc(patient, AppointmentStatus.COMPLETED));
        completed.removeIf(feedbackRepository::existsByAppointment);
        return completed;
    }

    @Transactional(readOnly = true)
    public List<Feedback> findForPatient(Patient patient) {
        return feedbackRepository.findByPatientOrderBySubmittedAtDesc(patient);
    }

    /** UC-05 doctor side: "my ratings". */
    @Transactional(readOnly = true)
    public List<Feedback> findForDoctor(Doctor doctor) {
        return feedbackRepository.findByDoctorOrderBySubmittedAtDesc(doctor);
    }

    @Transactional(readOnly = true)
    public List<Feedback> findAll() {
        return feedbackRepository.findAllByOrderBySubmittedAtDesc();
    }

    /** The Patient Relations Officer's queue. */
    @Transactional(readOnly = true)
    public List<Feedback> findUnanswered() {
        return feedbackRepository.findByOfficerResponseIsNullOrderBySubmittedAtAsc();
    }

    @Transactional(readOnly = true)
    public Feedback getFeedback(Long id) {
        return feedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found (id " + id + ")"));
    }

    /** Support side: the Patient Relations Officer replies to a patient's comment. */
    @Transactional
    public Feedback respond(Long feedbackId, User officer, String response) {
        if (response == null || response.isBlank()) {
            throw new BusinessException("Please type a response before saving.");
        }
        Feedback feedback = getFeedback(feedbackId);
        feedback.setOfficerResponse(response.trim());
        feedback.setRespondedAt(LocalDateTime.now());
        feedback.setRespondedBy(officer);
        return feedbackRepository.save(feedback);
    }

    /** Null when the doctor has no feedback yet. */
    @Transactional(readOnly = true)
    public Double averageRatingFor(Doctor doctor) {
        return feedbackRepository.findAverageRatingByDoctor(doctor);
    }

    @Transactional(readOnly = true)
    public long countFor(Doctor doctor) {
        return feedbackRepository.countByDoctor(doctor);
    }
}
