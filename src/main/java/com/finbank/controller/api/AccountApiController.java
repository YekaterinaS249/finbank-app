package com.finbank.controller.api;

import com.finbank.dto.AccountResponse;
import com.finbank.dto.TransactionResponse;
import com.finbank.exception.InvalidTransferException;
import com.finbank.model.Account;
import com.finbank.model.User;
import com.finbank.service.AccountService;
import com.finbank.service.TransferService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountApiController {

    private final AccountService accountService;
    private final TransferService transferService;
    private final CurrentUserResolver currentUserResolver;

    public AccountApiController(AccountService accountService, TransferService transferService,
                                 CurrentUserResolver currentUserResolver) {
        this.accountService = accountService;
        this.transferService = transferService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public List<AccountResponse> myAccounts(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return accountService.findByOwner(user).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/{accountNumber}/transactions")
    public List<TransactionResponse> transactions(@PathVariable String accountNumber, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Account account = accountService.getByAccountNumber(accountNumber);

        if (!account.getOwner().getId().equals(user.getId())) {
            throw new InvalidTransferException("You can only view your own account's transactions");
        }

        return transferService.history(account).stream()
                .map(TransactionResponse::from)
                .toList();
    }
}
