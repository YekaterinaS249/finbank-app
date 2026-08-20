package com.finbank.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for domain/business errors (insufficient funds, duplicate email,
 * unknown account, etc.) that should map to a 4xx response instead of a 500.
 */
public class BusinessRuleException extends RuntimeException {

    private final HttpStatus status;

    public BusinessRuleException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
