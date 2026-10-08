package com.healthconnect.repository;

import com.healthconnect.model.Doctor;
import com.healthconnect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByUser(User user);

    @Query("select d from Doctor d order by d.user.fullName")
    List<Doctor> findAllOrderByName();

    /** Only doctors whose login account is still active can be booked. */
    @Query("select d from Doctor d where d.user.active = true order by d.user.fullName")
    List<Doctor> findActiveDoctors();

    @Query("select d from Doctor d where d.user.active = true "
            + "and lower(d.specialization) = lower(:specialization) order by d.user.fullName")
    List<Doctor> findActiveBySpecialization(@Param("specialization") String specialization);

    /** Fills the "specialization" dropdown on the doctor search screen. */
    @Query("select distinct d.specialization from Doctor d where d.user.active = true order by d.specialization")
    List<String> findAllSpecializations();
}
