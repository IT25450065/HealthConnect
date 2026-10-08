package com.healthconnect.repository;

import com.healthconnect.model.Appointment;
import com.healthconnect.model.AppointmentStatus;
import com.healthconnect.model.AppointmentType;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * THE DOUBLE-BOOKING CHECK (UC-01 extension "appointment conflict").
     *
     * Spring reads the method name left to right and builds:
     *   select count(*) > 0 from appointment
     *   where doctor_id = ? and appointment_date_time = ? and status <> ?
     *
     * We pass CANCELLED as the excluded status so that a slot freed by a
     * cancellation becomes bookable again.
     */
    boolean existsByDoctorAndAppointmentDateTimeAndStatusNot(
            Doctor doctor, LocalDateTime appointmentDateTime, AppointmentStatus excludedStatus);

    /** Same idea from the patient's side: no two appointments at the same moment. */
    boolean existsByPatientAndAppointmentDateTimeAndStatusNot(
            Patient patient, LocalDateTime appointmentDateTime, AppointmentStatus excludedStatus);

    /** Everything already booked for a doctor on a given day - used to grey out taken slots. */
    List<Appointment> findByDoctorAndAppointmentDateTimeBetweenAndStatusNot(
            Doctor doctor, LocalDateTime from, LocalDateTime to, AppointmentStatus excludedStatus);

    List<Appointment> findByPatientOrderByAppointmentDateTimeDesc(Patient patient);

    List<Appointment> findByPatientAndTypeOrderByAppointmentDateTimeDesc(Patient patient, AppointmentType type);

    List<Appointment> findByPatientAndStatusOrderByAppointmentDateTimeDesc(Patient patient, AppointmentStatus status);

    List<Appointment> findByDoctorOrderByAppointmentDateTimeDesc(Doctor doctor);

    /** The doctor's work queue: everything not yet completed or cancelled. */
    List<Appointment> findByDoctorAndStatusInOrderByAppointmentDateTimeAsc(
            Doctor doctor, List<AppointmentStatus> statuses);

    List<Appointment> findAllByOrderByAppointmentDateTimeDesc();

    List<Appointment> findByStatusOrderByAppointmentDateTimeAsc(AppointmentStatus status);

    List<Appointment> findByAppointmentDateTimeBetweenOrderByAppointmentDateTimeAsc(
            LocalDateTime from, LocalDateTime to);

    long countByStatus(AppointmentStatus status);

    long countByType(AppointmentType type);

    long countByDoctor(Doctor doctor);

    long countByDoctorAndStatus(Doctor doctor, AppointmentStatus status);
}
