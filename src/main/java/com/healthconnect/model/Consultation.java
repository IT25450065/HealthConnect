package com.healthconnect.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * UC-02 Record Consultation Notes.
 *
 * One Consultation per Appointment. Because every Consultation hangs off an
 * Appointment, and every Appointment knows its Patient, the set of
 * Consultations for a patient IS that patient's medical history - which is
 * how the "View Patient Medical History" &lt;&lt;include&gt;&gt; step is
 * implemented (see ConsultationRepository.findByAppointmentPatientId).
 * There is deliberately no separate PatientRecord table duplicating this.
 */
@Entity
@Table(name = "consultation")
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consultation_id")
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @Column(name = "consultation_date_time", nullable = false)
    private LocalDateTime consultationDateTime = LocalDateTime.now();

    @Column(name = "symptoms", length = 1000)
    private String symptoms;

    @Column(name = "diagnosis", length = 1000)
    private String diagnosis;

    @Column(name = "notes", length = 2000)
    private String notes;

    /**
     * cascade = ALL means saving the Consultation also saves the Prescription
     * rows hanging off it, so the doctor's "save notes + prescription" form is
     * a single service call.
     */
    @OneToMany(mappedBy = "consultation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Prescription> prescriptions = new ArrayList<>();

    public Consultation() {
    }

    public Consultation(Appointment appointment) {
        this.appointment = appointment;
    }

    public void addPrescription(Prescription prescription) {
        prescriptions.add(prescription);
        prescription.setConsultation(this);
    }

    /** Shortcuts so templates can write consultation.patient instead of consultation.appointment.patient. */
    public Patient getPatient() {
        return appointment != null ? appointment.getPatient() : null;
    }

    public Doctor getDoctor() {
        return appointment != null ? appointment.getDoctor() : null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public LocalDateTime getConsultationDateTime() {
        return consultationDateTime;
    }

    public void setConsultationDateTime(LocalDateTime consultationDateTime) {
        this.consultationDateTime = consultationDateTime;
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

    public List<Prescription> getPrescriptions() {
        return prescriptions;
    }

    public void setPrescriptions(List<Prescription> prescriptions) {
        this.prescriptions = prescriptions;
    }

    public String getDateTimeLabel() {
        return DisplayFormat.dateTime(consultationDateTime);
    }
}
