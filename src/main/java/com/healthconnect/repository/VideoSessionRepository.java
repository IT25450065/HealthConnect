package com.healthconnect.repository;

import com.healthconnect.model.Appointment;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Patient;
import com.healthconnect.model.SessionStatus;
import com.healthconnect.model.VideoSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoSessionRepository extends JpaRepository<VideoSession, Long> {

    Optional<VideoSession> findByAppointment(Appointment appointment);

    @Query("select v from VideoSession v where v.appointment.patient = :patient "
            + "order by v.appointment.appointmentDateTime desc")
    List<VideoSession> findByPatient(@Param("patient") Patient patient);

    @Query("select v from VideoSession v where v.appointment.doctor = :doctor "
            + "order by v.appointment.appointmentDateTime asc")
    List<VideoSession> findByDoctor(@Param("doctor") Doctor doctor);

    List<VideoSession> findAllByOrderByCreatedAtDesc();

    long countByStatus(SessionStatus status);
}
