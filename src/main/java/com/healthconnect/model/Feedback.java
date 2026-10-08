package com.healthconnect.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * UC-05 View Patient Feedback and Ratings.
 *
 * ONE table only. The patient's "submit feedback" screen, the doctor's "my
 * ratings" screen, the Patient Relations Officer's support queue and the
 * admin's "Feedback Reports" screen all read these same rows - that is the
 * cross-cluster &lt;&lt;include&gt;&gt; from the use case diagram
 * ("View Patient Feedback Reports" includes "View Feedback and Ratings"),
 * implemented as shared data rather than a duplicated model.
 */
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    /** The visit being rated. Unique, so a patient can rate each visit once. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    /** 1 to 5. Enforced in FeedbackService and by a CHECK constraint in the schema. */
    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comments", length = 1000)
    private String comments;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    /** Support side: the Patient Relations Officer's reply, if any. */
    @Column(name = "officer_response", length = 1000)
    private String officerResponse;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @ManyToOne
    @JoinColumn(name = "responded_by_user_id")
    private User respondedBy;

    public Feedback() {
    }

    public Feedback(Patient patient, Doctor doctor, Appointment appointment, int rating, String comments) {
        this.patient = patient;
        this.doctor = doctor;
        this.appointment = appointment;
        this.rating = rating;
        this.comments = comments;
    }

    public boolean isAnswered() {
        return officerResponse != null && !officerResponse.isBlank();
    }

    /** "****-" for a 4 star rating - keeps the templates free of loops. */
    public String getStars() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            sb.append(i <= rating ? '*' : '-');
        }
        return sb.toString();
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

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getOfficerResponse() {
        return officerResponse;
    }

    public void setOfficerResponse(String officerResponse) {
        this.officerResponse = officerResponse;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }

    public User getRespondedBy() {
        return respondedBy;
    }

    public void setRespondedBy(User respondedBy) {
        this.respondedBy = respondedBy;
    }

    public String getSubmittedLabel() {
        return DisplayFormat.dateTime(submittedAt);
    }

    public String getRespondedLabel() {
        return DisplayFormat.dateTime(respondedAt);
    }
}
