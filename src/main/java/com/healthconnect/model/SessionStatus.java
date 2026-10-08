package com.healthconnect.model;

/**
 * Status of an online (video) consultation session.
 * Real video calling is out of scope for this project - see VideoSession.
 */
public enum SessionStatus {

    SCHEDULED("Scheduled"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String displayName;

    SessionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
