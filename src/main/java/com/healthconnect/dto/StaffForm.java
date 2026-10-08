package com.healthconnect.dto;

import com.healthconnect.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * UC-06: the Hospital Administrator's create/edit form for Doctor,
 * Receptionist, Pharmacist and Patient Relations Officer accounts.
 *
 * The doctor-only fields at the bottom are ignored unless role == DOCTOR.
 */
public class StaffForm {

    /** Null when creating, set when editing. */
    private Long userId;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name is too long")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    @Size(max = 20, message = "Phone number is too long")
    private String phone;

    @NotNull(message = "Please choose a role")
    private Role role;

    /** Required when creating. Left blank on edit means "keep current password". */
    private String password;

    private boolean active = true;

    // ----- Doctor-only fields -----

    @Size(max = 80, message = "Specialization is too long")
    private String specialization;

    @Size(max = 120, message = "Qualification is too long")
    private String qualification;

    @Size(max = 20, message = "Room number is too long")
    private String roomNo;

    private List<String> availableDays = new ArrayList<>();

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime availableFrom = LocalTime.of(9, 0);

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime availableTo = LocalTime.of(17, 0);

    public boolean isDoctor() {
        return role == Role.DOCTOR;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public String getQualification() {
        return qualification;
    }

    public void setQualification(String qualification) {
        this.qualification = qualification;
    }

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public List<String> getAvailableDays() {
        return availableDays;
    }

    public void setAvailableDays(List<String> availableDays) {
        this.availableDays = availableDays;
    }

    public LocalTime getAvailableFrom() {
        return availableFrom;
    }

    public void setAvailableFrom(LocalTime availableFrom) {
        this.availableFrom = availableFrom;
    }

    public LocalTime getAvailableTo() {
        return availableTo;
    }

    public void setAvailableTo(LocalTime availableTo) {
        this.availableTo = availableTo;
    }
}
