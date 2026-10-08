package com.healthconnect.repository;

import com.healthconnect.model.Appointment;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Feedback;
import com.healthconnect.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findAllByOrderBySubmittedAtDesc();

    List<Feedback> findByDoctorOrderBySubmittedAtDesc(Doctor doctor);

    List<Feedback> findByPatientOrderBySubmittedAtDesc(Patient patient);

    /** One feedback per visit - checked before a patient can submit. */
    boolean existsByAppointment(Appointment appointment);

    long countByDoctor(Doctor doctor);

    /** Unanswered feedback - the Patient Relations Officer's support queue. */
    List<Feedback> findByOfficerResponseIsNullOrderBySubmittedAtAsc();

    /**
     * Returns null (not 0) when a doctor has no feedback yet, which is why the
     * return type is the object Double and not the primitive double.
     */
    @Query("select avg(f.rating) from Feedback f where f.doctor = :doctor")
    Double findAverageRatingByDoctor(@Param("doctor") Doctor doctor);

    /** Hospital-wide average rating, for the admin feedback report. */
    @Query("select avg(f.rating) from Feedback f")
    Double findOverallAverageRating();
}
