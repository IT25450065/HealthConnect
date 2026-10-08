package com.healthconnect.exception;

/** UC-03 alternate flow: a prescription cannot be dispensed because stock is short. */
public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
