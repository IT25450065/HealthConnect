package com.healthconnect.exception;

/** UC-01 / UC-04 alternate flows: conflict, invalid date, outside working hours. */
public class BookingException extends BusinessException {

    public BookingException(String message) {
        super(message);
    }
}
