package com.healthconnect.service;

import com.healthconnect.dto.StaffForm;
import com.healthconnect.exception.BusinessException;
import com.healthconnect.exception.ResourceNotFoundException;
import com.healthconnect.model.Doctor;
import com.healthconnect.model.Patient;
import com.healthconnect.model.Role;
import com.healthconnect.model.User;
import com.healthconnect.repository.DoctorRepository;
import com.healthconnect.repository.PatientRepository;
import com.healthconnect.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * HOSPITAL ADMINISTRATION AND REPORTING MANAGEMENT MODULE - Vishalan S. -
 * UC-06 Manage Doctor and Staff Accounts.
 *
 * Only a Hospital Administrator reaches this service (enforced by
 * AuthInterceptor on the /admin/** URLs). Creating a doctor here creates TWO
 * rows: the login account in "users" and the professional profile in "doctor".
 */
@Service
public class AdminService {

    /** The account types an administrator is allowed to create. */
    public static final List<Role> STAFF_ROLES = Arrays.asList(
            Role.DOCTOR, Role.RECEPTIONIST, Role.PHARMACIST, Role.PATIENT_RELATIONS_OFFICER);

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository,
                        DoctorRepository doctorRepository,
                        PatientRepository patientRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> listStaff() {
        return userRepository.findByRoleInOrderByFullNameAsc(STAFF_ROLES);
    }

    @Transactional(readOnly = true)
    public List<Patient> listPatients() {
        return patientRepository.findAllOrderByName();
    }

    @Transactional(readOnly = true)
    public User getStaff(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff account not found (id " + userId + ")"));
        if (!STAFF_ROLES.contains(user.getRole())) {
            throw new BusinessException("That account is not a staff account.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public Doctor getDoctorProfile(User user) {
        return doctorRepository.findByUser(user).orElse(null);
    }

    /**
     * Creates a new staff account, or updates an existing one.
     *
     * Rules:
     *  - only the four STAFF_ROLES may be created here (patients register
     *    themselves; the administrator account is seeded)
     *  - email must be unique across the whole system
     *  - a password is required when creating; leaving it blank on edit keeps
     *    the current password
     *  - the role of an existing account cannot be changed, because a doctor
     *    has a profile row and appointment history that the other roles do not
     */
    @Transactional
    public User saveStaff(StaffForm form) {
        if (!STAFF_ROLES.contains(form.getRole())) {
            throw new BusinessException("Administrators can only create Doctor, Receptionist, "
                    + "Pharmacist and Patient Relations Officer accounts.");
        }

        String email = form.getEmail().trim().toLowerCase();
        boolean creating = form.getUserId() == null;
        User user;

        if (creating) {
            if (userRepository.existsByEmailIgnoreCase(email)) {
                throw new BusinessException("An account with the email " + email + " already exists.");
            }
            if (form.getPassword() == null || form.getPassword().length() < 6) {
                throw new BusinessException("Set a starting password of at least 6 characters.");
            }
            user = new User();
            user.setRole(form.getRole());
            user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        } else {
            user = getStaff(form.getUserId());
            if (user.getRole() != form.getRole()) {
                throw new BusinessException("The role of an existing account cannot be changed. "
                        + "Deactivate this account and create a new one instead.");
            }
            userRepository.findByEmailIgnoreCase(email)
                    .filter(other -> !other.getId().equals(user.getId()))
                    .ifPresent(other -> {
                        throw new BusinessException("Another account already uses the email " + email + ".");
                    });
            if (form.getPassword() != null && !form.getPassword().isBlank()) {
                if (form.getPassword().length() < 6) {
                    throw new BusinessException("The new password must be at least 6 characters.");
                }
                user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
            }
        }

        user.setFullName(form.getFullName().trim());
        user.setEmail(email);
        user.setPhone(form.getPhone());
        user.setActive(form.isActive());
        userRepository.save(user);

        if (form.getRole() == Role.DOCTOR) {
            saveDoctorProfile(user, form);
        }
        return user;
    }

    private void saveDoctorProfile(User user, StaffForm form) {
        if (form.getSpecialization() == null || form.getSpecialization().isBlank()) {
            throw new BusinessException("A doctor must have a specialization.");
        }
        if (form.getAvailableFrom() == null || form.getAvailableTo() == null
                || !form.getAvailableFrom().isBefore(form.getAvailableTo())) {
            throw new BusinessException("The 'available from' time must be earlier than the 'available to' time.");
        }
        List<String> days = form.getAvailableDays();
        if (days == null || days.isEmpty()) {
            throw new BusinessException("Select at least one day the doctor is available.");
        }

        Doctor doctor = doctorRepository.findByUser(user).orElseGet(() -> new Doctor(user, form.getSpecialization()));
        doctor.setSpecialization(form.getSpecialization().trim());
        doctor.setQualification(form.getQualification());
        doctor.setRoomNo(form.getRoomNo());
        doctor.setAvailableDays(String.join(",", days));
        doctor.setAvailableFrom(form.getAvailableFrom());
        doctor.setAvailableTo(form.getAvailableTo());
        doctorRepository.save(doctor);
    }

    /** Deactivate (or re-activate) an account. History is never deleted. */
    @Transactional
    public User setActive(Long userId, boolean active) {
        User user = getStaff(userId);
        user.setActive(active);
        return userRepository.save(user);
    }

    /** Fills the edit form with what is currently stored. */
    @Transactional(readOnly = true)
    public StaffForm buildStaffForm(Long userId) {
        User user = getStaff(userId);
        StaffForm form = new StaffForm();
        form.setUserId(user.getId());
        form.setFullName(user.getFullName());
        form.setEmail(user.getEmail());
        form.setPhone(user.getPhone());
        form.setRole(user.getRole());
        form.setActive(user.isActive());

        if (user.getRole() == Role.DOCTOR) {
            doctorRepository.findByUser(user).ifPresent(doctor -> {
                form.setSpecialization(doctor.getSpecialization());
                form.setQualification(doctor.getQualification());
                form.setRoomNo(doctor.getRoomNo());
                form.setAvailableFrom(doctor.getAvailableFrom());
                form.setAvailableTo(doctor.getAvailableTo());
                List<String> days = new ArrayList<>();
                for (DayOfWeek day : doctor.getAvailableDaySet()) {
                    days.add(day.name());
                }
                form.setAvailableDays(days);
            });
        }
        return form;
    }

    /** The checkbox list on the staff form. */
    public List<DayOfWeek> allDays() {
        return Arrays.asList(DayOfWeek.values());
    }
}
