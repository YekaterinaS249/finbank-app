package com.finbank.exception;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends BusinessRuleException {
    public AccountNotFoundException(String accountNumber) {
        super(HttpStatus.NOT_FOUND, "Account not found: " + accountNumber);
    }
}
