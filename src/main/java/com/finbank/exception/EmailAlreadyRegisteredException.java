package com.finbank.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends BusinessRuleException {
    public EmailAlreadyRegisteredException(String email) {
        super(HttpStatus.CONFLICT, "An account with email '" + email + "' already exists");
    }
}
