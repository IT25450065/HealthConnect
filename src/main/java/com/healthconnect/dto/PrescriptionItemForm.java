package com.healthconnect.dto;

/**
 * One medicine line inside ConsultationForm. Rows the doctor leaves blank
 * (no medicine selected) are simply skipped by ConsultationService.
 */
public class PrescriptionItemForm {

    private Long medicineId;
    private String dosage;
    private String instructions;
    private Integer quantity;

    public boolean isFilled() {
        return medicineId != null;
    }

    public Long getMedicineId() {
        return medicineId;
    }

    public void setMedicineId(Long medicineId) {
        this.medicineId = medicineId;
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
