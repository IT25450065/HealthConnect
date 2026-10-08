package com.healthconnect.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One medicine line on a prescription: which medicine, how much, how to take
 * it. The link to Medicine is what lets the pharmacist's "dispense" action
 * know exactly which stock rows to decrement.
 */
@Entity
@Table(name = "prescription_item")
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prescription_item_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @ManyToOne(optional = false)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    /** e.g. "500mg - twice daily" */
    @Column(name = "dosage", nullable = false, length = 100)
    private String dosage;

    /** e.g. "After meals" */
    @Column(name = "instructions", length = 255)
    private String instructions;

    /** Number of units to dispense - this is what is subtracted from stock. */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    public PrescriptionItem() {
    }

    public PrescriptionItem(Medicine medicine, String dosage, String instructions, int quantity) {
        this.medicine = medicine;
        this.dosage = dosage;
        this.instructions = instructions;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Prescription getPrescription() {
        return prescription;
    }

    public void setPrescription(Prescription prescription) {
        this.prescription = prescription;
    }

    public Medicine getMedicine() {
        return medicine;
    }

    public void setMedicine(Medicine medicine) {
        this.medicine = medicine;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
