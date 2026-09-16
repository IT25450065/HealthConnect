package com.healthconnect.model;

public enum PrescriptionStatus {

    PENDING("Pending Dispensing"),
    DISPENSED("Dispensed"),
    CANCELLED("Cancelled");

    private final String displayName;

    PrescriptionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
