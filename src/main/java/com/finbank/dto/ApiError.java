package com.finbank.dto;

import java.time.Instant;
import java.util.List;

/**
 * Uniform API error envelope. {@code error} is a stable top-level code
 * (e.g. "VALIDATION_ERROR", "CONFLICT", "NOT_FOUND"); {@code details}
 * carries per-field breakdowns for validation/conflict responses and is
 * empty for errors that aren't field-specific.
 */
public class ApiError {

    private Instant timestamp = Instant.now();
    private int status;
    private String error;
    private String message;
    private List<FieldError> details;
    private String path;

    public ApiError(int status, String error, String message, List<FieldError> details, String path) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.details = details;
        this.path = path;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public List<FieldError> getDetails() {
        return details;
    }

    public String getPath() {
        return path;
    }
}
