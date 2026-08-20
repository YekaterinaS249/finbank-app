package com.finbank.service;

import com.finbank.exception.AccountNotFoundException;
import com.finbank.model.Account;
import com.finbank.model.AccountType;
import com.finbank.model.User;
import com.finbank.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account openAccount(User owner, AccountType type, String currency, BigDecimal openingBalance) {
        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setType(type);
        account.setCurrency(currency);
        account.setBalance(openingBalance);
        account.setOwner(owner);
        return accountRepository.save(account);
    }

    public List<Account> findByOwner(User owner) {
        return accountRepository.findByOwner(owner);
    }

    public Account getByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private String generateAccountNumber() {
        String candidate;
        do {
            candidate = "FB" + String.format("%010d", (long) (random.nextDouble() * 10_000_000_000L));
        } while (accountRepository.findByAccountNumber(candidate).isPresent());
        return candidate;
    }
}
