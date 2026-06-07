package com.ganesh.commerce.inventory.exception;

/** Thrown when a SKU is unknown. Mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
