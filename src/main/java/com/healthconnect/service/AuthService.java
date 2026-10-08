package com.healthconnect.service;

import com.healthconnect.dto.PasswordResetForm;
import com.healthconnect.dto.ProfileForm;
import com.healthconnect.dto.RegistrationForm;
import com.healthconnect.dto.SessionUser;
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

/**
 * ACCOUNT AND ACCESS MODULE (shared - not owned by one member).
 *
 * Register Account, Login, Logout, Reset Password, Manage Profile. This is the
 * "System User" abstraction from the use case diagram: one place where every
 * actor's credentials are created and checked.
 *
 * Passwords are stored as BCrypt hashes. BCrypt is one-way: we can never turn
 * a stored hash back into the original password, we can only ask
 * passwordEncoder.matches(typedPassword, storedHash).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructor injection. Spring sees this single constructor and passes in
     * the repositories and the encoder automatically - no @Autowired needed.
     */
    public AuthService(UserRepository userRepository,
                       PatientRepository patientRepository,
                       DoctorRepository doctorRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Patient self-registration (part of the Appointment Management module). */
    @Transactional
    public Patient registerPatient(RegistrationForm form) {
        String email = form.getEmail() == null ? "" : form.getEmail().trim().toLowerCase();

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("The two passwords do not match.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("An account with the email " + email + " already exists.");
        }

        User user = new User(form.getFullName().trim(), email,
                passwordEncoder.encode(form.getPassword()), Role.PATIENT);
        user.setPhone(form.getPhone());
        userRepository.save(user);

        Patient patient = new Patient(user);
        patient.setDateOfBirth(form.getDateOfBirth());
        patient.setGender(form.getGender());
        patient.setAddress(form.getAddress());
        patient.setBloodGroup(form.getBloodGroup());
        return patientRepository.save(patient);
    }

    /**
     * Checks credentials and returns the object we will put in the HttpSession.
     *
     * The same message is used for "no such email" and "wrong password" on
     * purpose - telling an attacker which of the two was wrong would let them
     * discover which email addresses are registered.
     */
    @Transactional(readOnly = true)
    public SessionUser login(String email, String rawPassword) {
        String normalised = email == null ? "" : email.trim();

        User user = userRepository.findByEmailIgnoreCase(normalised)
                .orElseThrow(() -> new BusinessException("Invalid email or password."));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException("Invalid email or password.");
        }
        if (!user.isActive()) {
            throw new BusinessException("This account has been deactivated. Please contact the hospital administrator.");
        }
        return toSessionUser(user);
    }

    /** Builds the session object, including the patient/doctor profile id if there is one. */
    @Transactional(readOnly = true)
    public SessionUser toSessionUser(User user) {
        SessionUser sessionUser = new SessionUser(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
        if (user.getRole() == Role.PATIENT) {
            patientRepository.findByUser(user).ifPresent(p -> sessionUser.setPatientId(p.getId()));
        } else if (user.getRole() == Role.DOCTOR) {
            doctorRepository.findByUser(user).ifPresent(d -> sessionUser.setDoctorId(d.getId()));
        }
        return sessionUser;
    }

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found (id " + userId + ")"));
    }

    /** The Patient profile of the logged-in user. Used by every /patient/** screen. */
    @Transactional(readOnly = true)
    public Patient requirePatient(SessionUser sessionUser) {
        if (sessionUser == null || sessionUser.getPatientId() == null) {
            throw new ResourceNotFoundException("No patient profile is linked to this account.");
        }
        return patientRepository.findById(sessionUser.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found."));
    }

    /** The Doctor profile of the logged-in user. Used by every /doctor/** screen. */
    @Transactional(readOnly = true)
    public Doctor requireDoctor(SessionUser sessionUser) {
        if (sessionUser == null || sessionUser.getDoctorId() == null) {
            throw new ResourceNotFoundException("No doctor profile is linked to this account.");
        }
        return doctorRepository.findById(sessionUser.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found."));
    }

    @Transactional
    public void updateProfile(Long userId, ProfileForm form) {
        User user = getUser(userId);
        user.setFullName(form.getFullName().trim());
        user.setPhone(form.getPhone());
        userRepository.save(user);

        if (user.getRole() == Role.PATIENT) {
            patientRepository.findByUser(user).ifPresent(patient -> {
                patient.setDateOfBirth(form.getDateOfBirth());
                patient.setGender(form.getGender());
                patient.setAddress(form.getAddress());
                patient.setBloodGroup(form.getBloodGroup());
                patientRepository.save(patient);
            });
        }
    }

    /**
     * Reset Password.
     *
     * SIMPLIFICATION (documented in the README): emailing a reset link is out
     * of scope for this project, so identity is proved with the registered
     * email plus the registered phone number.
     */
    @Transactional
    public void resetPassword(PasswordResetForm form) {
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("The two passwords do not match.");
        }

        User user = userRepository.findByEmailIgnoreCase(form.getEmail().trim())
                .orElseThrow(() -> new BusinessException("No account matches that email and phone number."));

        String storedPhone = user.getPhone() == null ? "" : user.getPhone().replaceAll("\\s+", "");
        String typedPhone = form.getPhone().replaceAll("\\s+", "");
        if (storedPhone.isEmpty() || !storedPhone.equals(typedPhone)) {
            throw new BusinessException("No account matches that email and phone number.");
        }

        user.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        userRepository.save(user);
    }

    /** Fills the Manage Profile form with the values currently on file. */
    @Transactional(readOnly = true)
    public ProfileForm buildProfileForm(Long userId) {
        User user = getUser(userId);
        ProfileForm form = new ProfileForm();
        form.setFullName(user.getFullName());
        form.setPhone(user.getPhone());
        if (user.getRole() == Role.PATIENT) {
            patientRepository.findByUser(user).ifPresent(patient -> {
                form.setDateOfBirth(patient.getDateOfBirth());
                form.setGender(patient.getGender());
                form.setAddress(patient.getAddress());
                form.setBloodGroup(patient.getBloodGroup());
            });
        }
        return form;
    }
}
