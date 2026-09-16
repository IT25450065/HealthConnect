package com.healthconnect.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Date and time formatting used by the entities' "...Label" getters.
 *
 * Why not format in the Thymeleaf templates? Because the #temporals helper is
 * an optional Thymeleaf add-on, and keeping the formatting in Java means every
 * screen shows dates identically and the templates stay free of logic.
 */
public final class DisplayFormat {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_WITH_DAY =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private DisplayFormat() {
        // utility class - never instantiated
    }

    public static String date(LocalDate value) {
        return value == null ? "-" : value.format(DATE);
    }

    public static String dateWithDay(LocalDate value) {
        return value == null ? "-" : value.format(DATE_WITH_DAY);
    }

    public static String time(LocalTime value) {
        return value == null ? "-" : value.format(TIME);
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME);
    }
}
