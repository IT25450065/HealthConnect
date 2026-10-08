package com.healthconnect.dto;

import com.healthconnect.model.AppointmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Backs the "Book Appointment" form (UC-01) and the "Book Video Consultation"
 * form (UC-04) - both post the same three things: a doctor, a date and a slot.
 *
 * The slot is a plain String like "09:30" because it comes straight out of the
 * dropdown of free slots the service generated for that doctor and date.
 */
public class AppointmentForm {

    @NotNull(message = "Please choose a doctor")
    private Long doctorId;

    @NotNull(message = "Please choose a date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @NotNull(message = "Please choose a time slot")
    @Size(min = 1, message = "Please choose a time slot")
    private String slot;

    @Size(max = 255, message = "Reason is too long")
    private String reason;

    private AppointmentType type = AppointmentType.IN_PERSON;

    public Long getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Long doctorId) {
        this.doctorId = doctorId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public AppointmentType getType() {
        return type;
    }

    public void setType(AppointmentType type) {
        this.type = type;
    }
}
