package com.finbank.repository;

import com.finbank.model.Account;
import com.finbank.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountOrderByCreatedAtDesc(Account account);
}
