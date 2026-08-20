package com.finbank.dto;

/**
 * One validation failure: a stable machine-readable {@code code} (assert
 * on this in tests — it never changes) plus a human-readable
 * {@code message} (may be re-worded by copywriting without breaking tests).
 */
public class FieldError {

    private final String field;
    private final String code;
    private final String message;

    public FieldError(String field, String code, String message) {
        this.field = field;
        this.code = code;
        this.message = message;
    }

    public String getField() {
        return field;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
