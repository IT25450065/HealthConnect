package com.healthconnect.repository;

import com.healthconnect.model.Role;
import com.healthconnect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data writes the implementation of this interface for us at startup.
 * Extending JpaRepository already gives us save(), findById(), findAll(),
 * deleteById(), count() and more - we only declare the extra finders we need.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Used by the login screen. Spring turns this name into
     *  "select * from users where email = ?" automatically. */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRoleOrderByFullNameAsc(Role role);

    List<User> findByRoleInOrderByFullNameAsc(List<Role> roles);

    long countByRole(Role role);

    long countByActiveTrue();
}
