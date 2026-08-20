package com.finbank.repository;

import com.finbank.model.Account;
import com.finbank.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByOwner(User owner);

    Optional<Account> findByAccountNumber(String accountNumber);

    // Pessimistic lock so two concurrent transfers can't both read the same
    // stale balance and overdraw the account (a classic fintech race condition).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockByAccountNumber(String accountNumber);
}
