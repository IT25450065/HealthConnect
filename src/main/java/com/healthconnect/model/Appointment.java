package com.healthconnect.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * UC-01 Book Appointment / UC-04 Book Video Consultation.
 *
 * One Appointment row joins a Patient to a Doctor at a point in time. The
 * "type" column is what separates an in-person visit from a telemedicine
 * session, so both flows share this one table (the use case diagram shows
 * both generalising to "Conduct Consultation").
 *
 * Both relationships below are @ManyToOne: many appointments belong to one
 * patient, and many appointments belong to one doctor. @ManyToOne is EAGER by
 * default, which is what we want here - the templates print
 * appointment.doctor.fullName directly.
 */
@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "appointment_date_time", nullable = false)
    private LocalDateTime appointmentDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", nullable = false, length = 20)
    private AppointmentType type = AppointmentType.IN_PERSON;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Filled in once the doctor records the visit (UC-02). */
    @OneToOne(mappedBy = "appointment")
    private Consultation consultation;

    /** Only present when type == VIDEO (UC-04). */
    @OneToOne(mappedBy = "appointment")
    private VideoSession videoSession;

    public Appointment() {
    }

    public Appointment(Patient patient, Doctor doctor, LocalDateTime appointmentDateTime, AppointmentType type) {
        this.patient = patient;
        this.doctor = doctor;
        this.appointmentDateTime = appointmentDateTime;
        this.type = type;
    }

    public boolean isVideo() {
        return type == AppointmentType.VIDEO;
    }

    public boolean isCancellable() {
        return status == AppointmentStatus.PENDING || status == AppointmentStatus.CONFIRMED;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public LocalDateTime getAppointmentDateTime() {
        return appointmentDateTime;
    }

    public void setAppointmentDateTime(LocalDateTime appointmentDateTime) {
        this.appointmentDateTime = appointmentDateTime;
    }

    public AppointmentType getType() {
        return type;
    }

    public void setType(AppointmentType type) {
        this.type = type;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Consultation getConsultation() {
        return consultation;
    }

    public void setConsultation(Consultation consultation) {
        this.consultation = consultation;
    }

    public VideoSession getVideoSession() {
        return videoSession;
    }

    public void setVideoSession(VideoSession videoSession) {
        this.videoSession = videoSession;
    }

    // ---- display helpers used by the Thymeleaf templates ----

    public String getDateLabel() {
        return DisplayFormat.dateWithDay(appointmentDateTime == null ? null : appointmentDateTime.toLocalDate());
    }

    public String getTimeLabel() {
        return DisplayFormat.time(appointmentDateTime == null ? null : appointmentDateTime.toLocalTime());
    }

    public String getDateTimeLabel() {
        return DisplayFormat.dateTime(appointmentDateTime);
    }
}
