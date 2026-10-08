package com.healthconnect.service;

import com.healthconnect.dto.AppointmentForm;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.exception.ResourceNotFoundException;
import com.healthconnect.model.Appointment;
import com.healthconnect.model.AppointmentType;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Patient;
import com.healthconnect.model.SessionStatus;
import com.healthconnect.model.VideoSession;
import com.healthconnect.repository.VideoSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * TELEMEDICINE (ONLINE CONSULTATION) MODULE - Adithya J.M.O. -
 * UC-04 Book Video Consultation.
 *
 * NOTE HOW SHORT THIS CLASS IS. That is deliberate and it is the point of the
 * generalisation in the use case diagram ("Conduct In-Person Consultation" and
 * "Conduct Video Consultation" both generalise to "Conduct Consultation").
 *
 * Booking a video consultation is booking an ordinary appointment with
 * type = VIDEO, so this service CALLS AppointmentService rather than
 * re-implementing slot generation, working-hours checks and the double-booking
 * rule. All this class adds on top is the online session record.
 *
 * SCOPE BOUNDARY: real video calling (WebRTC, a media server, TURN/STUN) is
 * out of scope for this project. The "meeting link" is a generated placeholder
 * URL that is displayed to both sides; clicking it does not start a call.
 */
@Service
public class TelemedicineService {

    private static final String MEETING_BASE_URL = "https://meet.healthconnect.local/session/";

    private final AppointmentService appointmentService;
    private final VideoSessionRepository videoSessionRepository;

    public TelemedicineService(AppointmentService appointmentService,
                               VideoSessionRepository videoSessionRepository) {
        this.appointmentService = appointmentService;
        this.videoSessionRepository = videoSessionRepository;
    }

    /**
     * UC-04 main flow: reuse the whole appointment booking flow, then attach
     * an online session record to the appointment that came back.
     */
    @Transactional
    public VideoSession bookVideoConsultation(Patient patient, AppointmentForm form) {
        form.setType(AppointmentType.VIDEO);

        // Every UC-01 validation rule applies unchanged.
        Appointment appointment = appointmentService.bookAppointment(patient, form);

        VideoSession session = new VideoSession(appointment, generateMeetingLink());
        session.setStatus(SessionStatus.SCHEDULED);
        session.setPlatformNote("Placeholder link - live video calling is out of scope for this project.");
        appointment.setVideoSession(session);
        return videoSessionRepository.save(session);
    }

    private String generateMeetingLink() {
        return MEETING_BASE_URL + UUID.randomUUID().toString().substring(0, 8);
    }

    @Transactional(readOnly = true)
    public List<VideoSession> findForPatient(Patient patient) {
        return videoSessionRepository.findByPatient(patient);
    }

    @Transactional(readOnly = true)
    public List<VideoSession> findForDoctor(Doctor doctor) {
        return videoSessionRepository.findByDoctor(doctor);
    }

    @Transactional(readOnly = true)
    public List<VideoSession> findAll() {
        return videoSessionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public VideoSession getSession(Long id) {
        return videoSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Video session not found (id " + id + ")"));
    }

    /** The doctor marks the online session as started. */
    @Transactional
    public VideoSession startSession(Long sessionId, Doctor doctor) {
        VideoSession session = getSession(sessionId);
        requireOwningDoctor(session, doctor);
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new BusinessException("Only a scheduled session can be started.");
        }
        session.setStatus(SessionStatus.IN_PROGRESS);
        return videoSessionRepository.save(session);
    }

    @Transactional
    public VideoSession completeSession(Long sessionId, Doctor doctor) {
        VideoSession session = getSession(sessionId);
        requireOwningDoctor(session, doctor);
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new BusinessException("A cancelled session cannot be completed.");
        }
        session.setStatus(SessionStatus.COMPLETED);
        return videoSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public long countScheduled() {
        return videoSessionRepository.countByStatus(SessionStatus.SCHEDULED);
    }

    private void requireOwningDoctor(VideoSession session, Doctor doctor) {
        if (!session.getAppointment().getDoctor().getId().equals(doctor.getId())) {
            throw new BusinessException("This online session belongs to another doctor.");
        }
    }
}
