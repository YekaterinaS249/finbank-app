package com.finbank.exception;

import org.springframework.http.HttpStatus;

/** Same-account transfer, unauthorized source account, currency mismatch, etc. */
public class InvalidTransferException extends BusinessRuleException {
    public InvalidTransferException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
