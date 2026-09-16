package com.healthconnect.repository;

import com.healthconnect.model.Appointment;
import com.healthconnect.model.Consultation;
import com.healthconnect.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {

    Optional<Consultation> findByAppointment(Appointment appointment);

    boolean existsByAppointment(Appointment appointment);

    /**
     * THE "VIEW PATIENT MEDICAL HISTORY" INCLUDE (UC-02).
     *
     * A patient's history is simply every consultation attached to one of that
     * patient's appointments, newest first. This is a real query against real
     * rows - there is no separate PatientRecord table duplicating the data.
     */
    @Query("select c from Consultation c where c.appointment.patient.id = :patientId "
            + "order by c.consultationDateTime desc")
    List<Consultation> findPatientHistory(@Param("patientId") Long patientId);

    @Query("select c from Consultation c where c.appointment.doctor = :doctor "
            + "order by c.consultationDateTime desc")
    List<Consultation> findByDoctor(@Param("doctor") Doctor doctor);
}
