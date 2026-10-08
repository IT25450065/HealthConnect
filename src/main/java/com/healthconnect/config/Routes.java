package com.healthconnect.config;

import com.healthconnect.model.Role;

/**
 * One place that knows which dashboard each role lands on after login.
 * Used by the login handler and by the "/" redirect.
 */
public final class Routes {

    private Routes() {
    }

    public static String homeFor(Role role) {
        if (role == null) {
            return "/login";
        }
        switch (role) {
            case PATIENT:
                return "/patient/dashboard";
            case DOCTOR:
                return "/doctor/dashboard";
            case RECEPTIONIST:
                return "/reception/dashboard";
            case PHARMACIST:
                return "/pharmacy/dashboard";
            case ADMIN:
                return "/admin/dashboard";
            case PATIENT_RELATIONS_OFFICER:
                return "/support/dashboard";
            default:
                return "/login";
        }
    }
}
