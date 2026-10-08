package com.healthconnect.exception;

/** A row was requested by id and does not exist (usually a hand-edited URL). */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
