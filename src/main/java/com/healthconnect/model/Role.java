package com.healthconnect.model;

/**
 * The six concrete actors from the group's use case diagram.
 * Every User row carries exactly one of these, and the AuthInterceptor
 * uses it to decide which URLs that user is allowed to reach.
 */
public enum Role {

    PATIENT("Patient"),
    DOCTOR("Doctor"),
    RECEPTIONIST("Receptionist"),
    PHARMACIST("Pharmacist"),
    ADMIN("Hospital Administrator"),
    PATIENT_RELATIONS_OFFICER("Patient Relations Officer");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
