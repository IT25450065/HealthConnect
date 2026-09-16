package com.healthconnect.model;

/**
 * In the use case diagram "Conduct In-Person Consultation" and
 * "Conduct Video Consultation" both generalise to "Conduct Consultation".
 * We model that generalisation with one Appointment entity plus this type
 * flag, rather than two parallel appointment tables.
 */
public enum AppointmentType {

    IN_PERSON("In-Person"),
    VIDEO("Video Consultation");

    private final String displayName;

    AppointmentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
