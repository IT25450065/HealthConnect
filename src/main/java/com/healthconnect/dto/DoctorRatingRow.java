package com.healthconnect.dto;

/** One line of the admin/doctor "Feedback and Ratings Summary" report (UC-05, UC-06). */
public class DoctorRatingRow {

    private final Long doctorId;
    private final String doctorName;
    private final String specialization;
    private final long feedbackCount;
    private final Double averageRating;

    public DoctorRatingRow(Long doctorId, String doctorName, String specialization,
                           long feedbackCount, Double averageRating) {
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.specialization = specialization;
        this.feedbackCount = feedbackCount;
        this.averageRating = averageRating;
    }

    /** "4.25" or "-" when the doctor has no feedback yet. */
    public String getAverageLabel() {
        return averageRating == null ? "-" : String.format("%.2f", averageRating);
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getSpecialization() {
        return specialization;
    }

    public long getFeedbackCount() {
        return feedbackCount;
    }

    public Double getAverageRating() {
        return averageRating;
    }
}
