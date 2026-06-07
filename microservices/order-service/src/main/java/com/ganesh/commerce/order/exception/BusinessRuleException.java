package com.ganesh.commerce.order.exception;

import org.springframework.http.HttpStatus;

/**
 * Domain rule violation. Carries a stable machine {@code code} for downstream
 * consumers and the HTTP {@code status} to map to (default 422).
 * COMM-201 throws this with {@code OUT_OF_STOCK} / 409.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessRuleException(String code, String message) {
        this(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessRuleException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
