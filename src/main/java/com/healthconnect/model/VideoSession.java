package com.healthconnect.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * UC-04 Book Video Consultation (Telemedicine module).
 *
 * SCOPE NOTE: real video calling (WebRTC, a media server, and so on) is
 * explicitly out of scope for this project. A VideoSession is therefore a
 * SCHEDULED ONLINE SESSION RECORD: it stores a generated meeting link and a
 * session status. Clicking "join" in the UI does not start a call - it shows
 * the link. Everything else about the booking (slot validation, double-booking
 * prevention, the doctor's queue) is reused from the normal Appointment flow
 * rather than duplicated here.
 */
@Entity
@Table(name = "video_session")
public class VideoSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_session_id")
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    /** Placeholder meeting URL generated at booking time. */
    @Column(name = "meeting_link", nullable = false, length = 255)
    private String meetingLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.SCHEDULED;

    @Column(name = "platform_note", length = 255)
    private String platformNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public VideoSession() {
    }

    public VideoSession(Appointment appointment, String meetingLink) {
        this.appointment = appointment;
        this.meetingLink = meetingLink;
    }

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

    public String getMeetingLink() {
        return meetingLink;
    }

    public void setMeetingLink(String meetingLink) {
        this.meetingLink = meetingLink;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getPlatformNote() {
        return platformNote;
    }

    public void setPlatformNote(String platformNote) {
        this.platformNote = platformNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedLabel() {
        return DisplayFormat.dateTime(createdAt);
    }

    public String getScheduledLabel() {
        return appointment == null ? "-" : appointment.getDateTimeLabel();
    }
}
