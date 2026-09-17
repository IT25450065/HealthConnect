package com.healthconnect.repository;

import com.healthconnect.model.Patient;
import com.healthconnect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    /** Find the patient profile that belongs to a logged-in account. */
    Optional<Patient> findByUser(User user);

    /**
     * Ordering by a field of a RELATED entity (user.fullName) is clearer as a
     * short JPQL query than as a very long method name.
     */
    @Query("select p from Patient p order by p.user.fullName")
    List<Patient> findAllOrderByName();
}
