package com.healthconnect.dto;

import com.healthconnect.model.Role;

import java.io.Serializable;

/**
 * The small object we put in the HttpSession after a successful login.
 *
 * We deliberately do NOT put the User entity itself in the session: a detached
 * entity in an HTTP session is a classic source of lazy-loading bugs. This
 * carries only the handful of values the navbar and the AuthInterceptor need.
 */
public class SessionUser implements Serializable {

    private Long userId;
    private String fullName;
    private String email;
    private Role role;

    /** Set only when role == PATIENT. Saves a lookup on every patient screen. */
    private Long patientId;

    /** Set only when role == DOCTOR. */
    private Long doctorId;

    public SessionUser() {
    }

    public SessionUser(Long userId, String fullName, String email, Role role) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public String getRoleLabel() {
        return role != null ? role.getDisplayName() : "";
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Long doctorId) {
        this.doctorId = doctorId;
    }
}
