package com.healthconnect.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The digital prescription created by the doctor in UC-02 and consumed by the
 * pharmacist in UC-03. The header lives here; the actual medicine lines live
 * in PrescriptionItem so one prescription can contain several medicines.
 */
@Entity
@Table(name = "prescription")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prescription_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PrescriptionStatus status = PrescriptionStatus.PENDING;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name = "dispensed_at")
    private LocalDateTime dispensedAt;

    /** Which pharmacist dispensed it. Null until it is dispensed. */
    @ManyToOne
    @JoinColumn(name = "dispensed_by_user_id")
    private User dispensedBy;

    @Column(name = "pharmacist_note", length = 500)
    private String pharmacistNote;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrescriptionItem> items = new ArrayList<>();

    public Prescription() {
    }

    public Prescription(Consultation consultation) {
        this.consultation = consultation;
    }

    /** Keeps both sides of the relationship in step - always add items through here. */
    public void addItem(PrescriptionItem item) {
        items.add(item);
        item.setPrescription(this);
    }

    public Patient getPatient() {
        return consultation != null ? consultation.getPatient() : null;
    }

    public Doctor getDoctor() {
        return consultation != null ? consultation.getDoctor() : null;
    }

    public boolean isPending() {
        return status == PrescriptionStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Consultation getConsultation() {
        return consultation;
    }

    public void setConsultation(Consultation consultation) {
        this.consultation = consultation;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }

    public void setStatus(PrescriptionStatus status) {
        this.status = status;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getDispensedAt() {
        return dispensedAt;
    }

    public void setDispensedAt(LocalDateTime dispensedAt) {
        this.dispensedAt = dispensedAt;
    }

    public User getDispensedBy() {
        return dispensedBy;
    }

    public void setDispensedBy(User dispensedBy) {
        this.dispensedBy = dispensedBy;
    }

    public String getPharmacistNote() {
        return pharmacistNote;
    }

    public void setPharmacistNote(String pharmacistNote) {
        this.pharmacistNote = pharmacistNote;
    }

    public List<PrescriptionItem> getItems() {
        return items;
    }

    public void setItems(List<PrescriptionItem> items) {
        this.items = items;
    }

    public String getIssuedLabel() {
        return DisplayFormat.dateTime(issuedAt);
    }

    public String getDispensedLabel() {
        return DisplayFormat.dateTime(dispensedAt);
    }
}
