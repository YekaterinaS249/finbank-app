package com.finbank.exception;

import com.finbank.dto.FieldError;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Carries one or more {@link FieldError}s at once (collect-all, not
 * fail-fast — see the Registration Field Validation spec). Format/boundary
 * rule violations use 422 UNPROCESSABLE_ENTITY; duplicate email/phone use
 * 409 CONFLICT. Format checks always run first, so a single response never
 * mixes 422-class and 409-class causes.
 */
public class RegistrationValidationException extends RuntimeException {

    private final HttpStatus status;
    private final List<FieldError> errors;

    public RegistrationValidationException(HttpStatus status, List<FieldError> errors) {
        super("Registration request failed validation: " + errors.size() + " error(s)");
        this.status = status;
        this.errors = errors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
