package com.healthconnect.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * UC-02: the doctor's single form that records consultation notes AND the
 * digital prescription in one submit.
 *
 * "items" is pre-filled with MAX_ITEMS blank rows so Thymeleaf can render a
 * fixed grid of medicine lines and Spring can bind them back as
 * items[0].medicineId, items[1].medicineId, ... - no JavaScript needed.
 */
public class ConsultationForm {

    public static final int MAX_ITEMS = 5;

    @NotNull
    private Long appointmentId;

    @Size(max = 1000, message = "Symptoms text is too long")
    private String symptoms;

    @NotBlank(message = "Diagnosis is required")
    @Size(max = 1000, message = "Diagnosis is too long")
    private String diagnosis;

    @Size(max = 2000, message = "Notes are too long")
    private String notes;

    @Valid
    private List<PrescriptionItemForm> items = new ArrayList<>();

    public ConsultationForm() {
        for (int i = 0; i < MAX_ITEMS; i++) {
            items.add(new PrescriptionItemForm());
        }
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<PrescriptionItemForm> getItems() {
        return items;
    }

    public void setItems(List<PrescriptionItemForm> items) {
        this.items = items;
    }
}
