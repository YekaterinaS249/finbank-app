package com.finbank.dto;

import com.finbank.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;

public class TransactionResponse {

    private Long id;
    private String transferRef;
    private String direction;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String counterpartyAccountNumber;
    private String description;
    private Instant createdAt;

    public static TransactionResponse from(Transaction tx) {
        TransactionResponse dto = new TransactionResponse();
        dto.id = tx.getId();
        dto.transferRef = tx.getTransferRef();
        dto.direction = tx.getDirection().name();
        dto.amount = tx.getAmount();
        dto.balanceAfter = tx.getBalanceAfter();
        dto.counterpartyAccountNumber = tx.getCounterpartyAccountNumber();
        dto.description = tx.getDescription();
        dto.createdAt = tx.getCreatedAt();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getTransferRef() {
        return transferRef;
    }

    public String getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getCounterpartyAccountNumber() {
        return counterpartyAccountNumber;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
