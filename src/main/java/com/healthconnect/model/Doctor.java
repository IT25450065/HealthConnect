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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Doctor profile: specialization plus the weekly availability window that
 * AppointmentService uses to generate bookable slots and to reject bookings
 * that fall outside the doctor's working hours.
 *
 * availableDays is stored as a simple comma-separated list of
 * java.time.DayOfWeek names, for example "MONDAY,TUESDAY,WEDNESDAY". A child
 * table would be more "correct" relationally, but a single column keeps the
 * SQL Server schema small and is easy to read in SSMS.
 */
@Entity
@Table(name = "doctor")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doctor_id")
    private Long id;

    @OneToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "specialization", nullable = false, length = 80)
    private String specialization;

    @Column(name = "qualification", length = 120)
    private String qualification;

    @Column(name = "room_no", length = 20)
    private String roomNo;

    @Column(name = "available_days", nullable = false, length = 120)
    private String availableDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";

    @Column(name = "available_from", nullable = false)
    private LocalTime availableFrom = LocalTime.of(9, 0);

    @Column(name = "available_to", nullable = false)
    private LocalTime availableTo = LocalTime.of(17, 0);

    @OneToMany(mappedBy = "doctor")
    private List<Appointment> appointments = new ArrayList<>();

    public Doctor() {
    }

    public Doctor(User user, String specialization) {
        this.user = user;
        this.specialization = specialization;
    }

    /**
     * Parses availableDays into real DayOfWeek values.
     *
     * Hibernate uses FIELD access on this class (because @Id is on a field),
     * so helper getters like this one are NOT treated as persistent
     * properties - they are just plain Java methods the services and the
     * Thymeleaf templates can call.
     */
    public Set<DayOfWeek> getAvailableDaySet() {
        Set<DayOfWeek> days = new LinkedHashSet<>();
        if (availableDays == null || availableDays.isBlank()) {
            return days;
        }
        for (String token : availableDays.split(",")) {
            String name = token.trim().toUpperCase();
            if (!name.isEmpty()) {
                days.add(DayOfWeek.valueOf(name));
            }
        }
        return days;
    }

    public boolean isAvailableOn(DayOfWeek day) {
        return getAvailableDaySet().contains(day);
    }

    /** "Mon, Tue, Wed" - for display in the doctor list. */
    public String getAvailableDaysLabel() {
        StringBuilder sb = new StringBuilder();
        for (DayOfWeek day : getAvailableDaySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String name = day.name();
            sb.append(name.charAt(0)).append(name.substring(1, 3).toLowerCase());
        }
        return sb.toString();
    }

    public String getFullName() {
        return user != null ? user.getFullName() : "";
    }

    public String getEmail() {
        return user != null ? user.getEmail() : "";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public String getAvailableDays() {
        return availableDays;
    }

    public void setAvailableDays(String availableDays) {
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

    public List<Appointment> getAppointments() {
        return appointments;
    }

    public void setAppointments(List<Appointment> appointments) {
        this.appointments = appointments;
    }

    /** "09:00 - 17:00" */
    public String getHoursLabel() {
        return DisplayFormat.time(availableFrom) + " - " + DisplayFormat.time(availableTo);
    }
}
