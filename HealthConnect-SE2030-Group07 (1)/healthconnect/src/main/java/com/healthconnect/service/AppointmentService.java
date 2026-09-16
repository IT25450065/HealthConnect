package com.healthconnect.service;

import com.healthconnect.dto.AppointmentForm;
import com.healthconnect.exception.BookingException;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.exception.ResourceNotFoundException;
import com.healthconnect.model.Appointment;
import com.healthconnect.model.AppointmentStatus;
import com.healthconnect.model.AppointmentType;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Patient;
import com.healthconnect.repository.AppointmentRepository;
import com.healthconnect.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * APPOINTMENT MANAGEMENT MODULE - Rathnayaka R.M.C.R.B. - UC-01 Book Appointment.
 *
 * This is where ALL the booking rules live. The controller only collects the
 * form and shows the result; every decision about whether a booking is legal
 * is made here, so the same rules apply no matter which screen calls it - the
 * patient's own booking page, the telemedicine page (UC-04) or a receptionist
 * booking on a patient's behalf.
 *
 * The rules implement the UC-01 extension flows:
 *   1. date/time in the past          -> rejected
 *   2. date too far ahead             -> rejected
 *   3. doctor does not work that day   -> rejected
 *   4. time outside working hours      -> rejected
 *   5. time is not on a slot boundary  -> rejected
 *   6. doctor already booked           -> rejected  (the "appointment conflict" extension)
 *   7. patient already booked          -> rejected
 */
@Service
public class AppointmentService {

    /** Every consultation is half an hour. */
    public static final int SLOT_MINUTES = 30;

    /** How far ahead a patient may book. */
    public static final int MAX_DAYS_AHEAD = 60;

    private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    public AppointmentService(AppointmentRepository appointmentRepository, DoctorRepository doctorRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
    }

    // ------------------------------------------------------------------
    // Searching for a doctor
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Doctor> searchDoctors(String specialization) {
        if (specialization == null || specialization.isBlank() || "ALL".equalsIgnoreCase(specialization)) {
            return doctorRepository.findActiveDoctors();
        }
        return doctorRepository.findActiveBySpecialization(specialization);
    }

    @Transactional(readOnly = true)
    public List<String> listSpecializations() {
        return doctorRepository.findAllSpecializations();
    }

    @Transactional(readOnly = true)
    public Doctor getDoctor(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found (id " + doctorId + ")"));
    }

    @Transactional(readOnly = true)
    public List<Doctor> listAllDoctors() {
        return doctorRepository.findAllOrderByName();
    }

    // ------------------------------------------------------------------
    // Slot generation - powers the time dropdown on the booking form
    // ------------------------------------------------------------------

    /**
     * Every half-hour slot the doctor works on that date, minus the ones that
     * are already taken and minus any that have already passed today.
     *
     * An empty list is the UC-01 "no slots available" alternate flow, and the
     * booking page shows a message instead of an empty dropdown.
     */
    @Transactional(readOnly = true)
    public List<String> findFreeSlots(Doctor doctor, LocalDate date) {
        List<String> free = new ArrayList<>();
        if (doctor == null || date == null) {
            return free;
        }
        if (!doctor.isAvailableOn(date.getDayOfWeek())) {
            return free;
        }

        // Which times are already booked on that day?
        Set<LocalTime> taken = new HashSet<>();
        List<Appointment> booked = appointmentRepository
                .findByDoctorAndAppointmentDateTimeBetweenAndStatusNot(
                        doctor, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), AppointmentStatus.CANCELLED);
        for (Appointment appointment : booked) {
            taken.add(appointment.getAppointmentDateTime().toLocalTime());
        }

        LocalDateTime now = LocalDateTime.now();
        int start = minutesOf(doctor.getAvailableFrom());
        int end = minutesOf(doctor.getAvailableTo());

        for (int minute = start; minute + SLOT_MINUTES <= end; minute += SLOT_MINUTES) {
            LocalTime slot = LocalTime.of(minute / 60, minute % 60);
            if (taken.contains(slot)) {
                continue;
            }
            if (!LocalDateTime.of(date, slot).isAfter(now)) {
                continue; // already in the past
            }
            free.add(slot.format(SLOT_FORMAT));
        }
        return free;
    }

    // ------------------------------------------------------------------
    // UC-01 main flow: book
    // ------------------------------------------------------------------

    /**
     * Creates the appointment, or throws BookingException with a message the
     * controller shows on the page in red.
     */
    @Transactional
    public Appointment bookAppointment(Patient patient, AppointmentForm form) {
        Doctor doctor = getDoctor(form.getDoctorId());
        String doctorName = doctor.getFullName();

        if (!doctor.getUser().isActive()) {
            throw new BookingException(doctorName + " is not currently accepting appointments.");
        }

        LocalTime time = parseSlot(form.getSlot());
        LocalDate date = form.getDate();
        LocalDateTime when = LocalDateTime.of(date, time);

        // (1) The slot must still be in the future.
        if (!when.isAfter(LocalDateTime.now())) {
            throw new BookingException("That date and time has already passed. Please choose a future slot.");
        }

        // (2) ... but not unreasonably far ahead.
        if (date.isAfter(LocalDate.now().plusDays(MAX_DAYS_AHEAD))) {
            throw new BookingException("Appointments can only be booked up to " + MAX_DAYS_AHEAD + " days in advance.");
        }

        // (3) The doctor must work on that day of the week.
        if (!doctor.isAvailableOn(date.getDayOfWeek())) {
            throw new BookingException(doctorName + " does not consult on "
                    + capitalise(date.getDayOfWeek().name()) + "s. Available days: " + doctor.getAvailableDaysLabel() + ".");
        }

        // (4) The slot must sit inside the working hours.
        int slotStart = minutesOf(time);
        int workStart = minutesOf(doctor.getAvailableFrom());
        int workEnd = minutesOf(doctor.getAvailableTo());
        if (slotStart < workStart || slotStart + SLOT_MINUTES > workEnd) {
            throw new BookingException(doctorName + " consults between "
                    + doctor.getAvailableFrom().format(SLOT_FORMAT) + " and "
                    + doctor.getAvailableTo().format(SLOT_FORMAT) + ".");
        }

        // (5) The slot must line up with the half-hour grid.
        if ((slotStart - workStart) % SLOT_MINUTES != 0) {
            throw new BookingException("Appointments start every " + SLOT_MINUTES
                    + " minutes. Please pick a slot from the list.");
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

        Appointment appointment = new Appointment(patient, doctor, when,
                form.getType() == null ? AppointmentType.IN_PERSON : form.getType());
        appointment.setReason(form.getReason());
        appointment.setStatus(AppointmentStatus.PENDING);
        return appointmentRepository.save(appointment);
    }

    // ------------------------------------------------------------------
    // Managing existing appointments (receptionist / admin / patient)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Appointment getAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found (id " + id + ")"));
    }

    @Transactional
    public Appointment confirmAppointment(Long id) {
        Appointment appointment = getAppointment(id);
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BusinessException("Only a pending appointment can be confirmed.");
        }
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        return appointmentRepository.save(appointment);
    }

    /**
     * Cancels an appointment. When patientId is not null the caller is a
     * patient, and we refuse to touch anyone else's booking - this stops a
     * patient cancelling another patient's appointment by editing the URL.
     */
    @Transactional
    public Appointment cancelAppointment(Long id, Long patientIdOrNull) {
        Appointment appointment = getAppointment(id);

        if (patientIdOrNull != null && !appointment.getPatient().getId().equals(patientIdOrNull)) {
            throw new BusinessException("You can only cancel your own appointments.");
        }
        if (!appointment.isCancellable()) {
            throw new BusinessException("A " + appointment.getStatus().getDisplayName().toLowerCase()
                    + " appointment cannot be cancelled.");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        if (appointment.getVideoSession() != null) {
            appointment.getVideoSession().setStatus(com.healthconnect.model.SessionStatus.CANCELLED);
        }
        return appointmentRepository.save(appointment);
    }

    // ------------------------------------------------------------------
    // Listings
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Appointment> findForPatient(Patient patient) {
        return appointmentRepository.findByPatientOrderByAppointmentDateTimeDesc(patient);
    }

    @Transactional(readOnly = true)
    public List<Appointment> findForPatientByType(Patient patient, AppointmentType type) {
        return appointmentRepository.findByPatientAndTypeOrderByAppointmentDateTimeDesc(patient, type);
    }

    @Transactional(readOnly = true)
    public List<Appointment> findCompletedForPatient(Patient patient) {
        return appointmentRepository.findByPatientAndStatusOrderByAppointmentDateTimeDesc(
                patient, AppointmentStatus.COMPLETED);
    }

    @Transactional(readOnly = true)
    public List<Appointment> findAll() {
        return appointmentRepository.findAllByOrderByAppointmentDateTimeDesc();
    }

    @Transactional(readOnly = true)
    public List<Appointment> findByStatus(AppointmentStatus status) {
        return appointmentRepository.findByStatusOrderByAppointmentDateTimeAsc(status);
    }

    @Transactional(readOnly = true)
    public List<Appointment> findToday() {
        LocalDate today = LocalDate.now();
        return appointmentRepository.findByAppointmentDateTimeBetweenOrderByAppointmentDateTimeAsc(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    /** The doctor's queue: booked visits that still need to be seen (UC-02 step 1). */
    @Transactional(readOnly = true)
    public List<Appointment> findQueueForDoctor(Doctor doctor) {
        return appointmentRepository.findByDoctorAndStatusInOrderByAppointmentDateTimeAsc(
                doctor, Arrays.asList(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));
    }

    @Transactional(readOnly = true)
    public List<Appointment> findAllForDoctor(Doctor doctor) {
        return appointmentRepository.findByDoctorOrderByAppointmentDateTimeDesc(doctor);
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    /** Minutes since midnight - avoids awkward LocalTime arithmetic near midnight. */
    private int minutesOf(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private LocalTime parseSlot(String slot) {
        try {
            return LocalTime.parse(slot.trim());
        } catch (RuntimeException ex) {
            throw new BookingException("\"" + slot + "\" is not a valid time slot.");
        }
    }

    private String capitalise(String word) {
        return word.charAt(0) + word.substring(1).toLowerCase();
    }
}
