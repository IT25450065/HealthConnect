package com.healthconnect.dto;

/** One line of the admin "Appointments Summary" report (UC-06 reporting). */
public class DoctorAppointmentRow {

    private final String doctorName;
    private final String specialization;
    private final long total;
    private final long pending;
    private final long confirmed;
    private final long completed;
    private final long cancelled;

    public DoctorAppointmentRow(String doctorName, String specialization, long total,
                                long pending, long confirmed, long completed, long cancelled) {
        this.doctorName = doctorName;
        this.specialization = specialization;
        this.total = total;
        this.pending = pending;
        this.confirmed = confirmed;
        this.completed = completed;
        this.cancelled = cancelled;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getSpecialization() {
        return specialization;
    }

    public long getTotal() {
        return total;
    }

    public long getPending() {
        return pending;
    }

    public long getConfirmed() {
        return confirmed;
    }

    public long getCompleted() {
        return completed;
    }

    public long getCancelled() {
        return cancelled;
    }
}
