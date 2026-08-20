package com.finbank.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends BusinessRuleException {
    public InsufficientFundsException(String accountNumber) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds on account " + accountNumber);
    }
}
