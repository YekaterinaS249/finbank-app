package com.finbank.service;

import com.finbank.dto.TransferRequest;
import com.finbank.exception.AccountNotFoundException;
import com.finbank.exception.InsufficientFundsException;
import com.finbank.exception.InvalidTransferException;
import com.finbank.model.Account;
import com.finbank.model.Transaction;
import com.finbank.model.TransactionDirection;
import com.finbank.model.User;
import com.finbank.repository.AccountRepository;
import com.finbank.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransferService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Moves money between two accounts. {@code actor} must own the source
     * account — FinBank never lets you debit an account you don't own.
     */
    @Transactional
    public String transfer(User actor, TransferRequest request) {
        if (request.getFromAccountNumber().equals(request.getToAccountNumber())) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Amount must be greater than zero");
        }

        // Lock ordering by account number keeps concurrent transfers between
        // the same two accounts from deadlocking each other.
        String first = request.getFromAccountNumber().compareTo(request.getToAccountNumber()) < 0
                ? request.getFromAccountNumber() : request.getToAccountNumber();
        String second = first.equals(request.getFromAccountNumber())
                ? request.getToAccountNumber() : request.getFromAccountNumber();

        Account lockedFirst = lockAccount(first);
        Account lockedSecond = lockAccount(second);

        Account from = lockedFirst.getAccountNumber().equals(request.getFromAccountNumber()) ? lockedFirst : lockedSecond;
        Account to = lockedFirst.getAccountNumber().equals(request.getToAccountNumber()) ? lockedFirst : lockedSecond;

        if (!from.getOwner().getId().equals(actor.getId())) {
            throw new InvalidTransferException("You can only transfer from your own account");
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new InvalidTransferException("Cross-currency transfers are not supported yet ("
                    + from.getCurrency() + " -> " + to.getCurrency() + ")");
        }
        if (from.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(from.getAccountNumber());
        }

        from.setBalance(from.getBalance().subtract(request.getAmount()));
        to.setBalance(to.getBalance().add(request.getAmount()));
        accountRepository.save(from);
        accountRepository.save(to);

        String transferRef = UUID.randomUUID().toString();

        Transaction debit = new Transaction();
        debit.setTransferRef(transferRef);
        debit.setAccount(from);
        debit.setDirection(TransactionDirection.DEBIT);
        debit.setAmount(request.getAmount());
        debit.setBalanceAfter(from.getBalance());
        debit.setCounterpartyAccountNumber(to.getAccountNumber());
        debit.setDescription(request.getDescription());
        transactionRepository.save(debit);

        Transaction credit = new Transaction();
        credit.setTransferRef(transferRef);
        credit.setAccount(to);
        credit.setDirection(TransactionDirection.CREDIT);
        credit.setAmount(request.getAmount());
        credit.setBalanceAfter(to.getBalance());
        credit.setCounterpartyAccountNumber(from.getAccountNumber());
        credit.setDescription(request.getDescription());
        transactionRepository.save(credit);

        return transferRef;
    }

    public List<Transaction> history(Account account) {
        return transactionRepository.findByAccountOrderByCreatedAtDesc(account);
    }

    private Account lockAccount(String accountNumber) {
        return accountRepository.findWithLockByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }
}
