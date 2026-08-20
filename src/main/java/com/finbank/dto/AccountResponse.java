package com.finbank.dto;

import com.finbank.model.Account;

import java.math.BigDecimal;

public class AccountResponse {

    private Long id;
    private String accountNumber;
    private String type;
    private String currency;
    private BigDecimal balance;

    public static AccountResponse from(Account account) {
        AccountResponse dto = new AccountResponse();
        dto.id = account.getId();
        dto.accountNumber = account.getAccountNumber();
        dto.type = account.getType().name();
        dto.currency = account.getCurrency();
        dto.balance = account.getBalance();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
