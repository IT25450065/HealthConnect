package com.healthconnect.exception;

/**
 * Thrown by the SERVICE layer when a business rule is broken - for example
 * "that slot is already booked" or "not enough stock".
 *
 * It is a RuntimeException, so services do not need "throws" clauses. Every
 * controller catches it and turns getMessage() into a red message on the page,
 * which is how the alternate flows of the use cases reach the user.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
