package com.healthconnect.repository;

import com.healthconnect.model.Prescription;
import com.healthconnect.model.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    /** The pharmacist's work queue - oldest prescription first. */
    List<Prescription> findByStatusOrderByIssuedAtAsc(PrescriptionStatus status);

    List<Prescription> findAllByOrderByIssuedAtDesc();

    @Query("select p from Prescription p where p.consultation.appointment.patient.id = :patientId "
            + "order by p.issuedAt desc")
    List<Prescription> findByPatientId(@Param("patientId") Long patientId);

    long countByStatus(PrescriptionStatus status);
}
