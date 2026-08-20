package com.finbank.controller.web;

import com.finbank.model.Account;
import com.finbank.model.User;
import com.finbank.repository.UserRepository;
import com.finbank.service.AccountService;
import com.finbank.service.TransferService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class DashboardController {

    private final AccountService accountService;
    private final TransferService transferService;
    private final UserRepository userRepository;

    public DashboardController(AccountService accountService, TransferService transferService,
                                UserRepository userRepository) {
        this.accountService = accountService;
        this.transferService = transferService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("accounts", accountService.findByOwner(user));
        return "dashboard";
    }

    @GetMapping("/accounts/{accountNumber}/transactions")
    public String transactions(@PathVariable String accountNumber, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        Account account = accountService.getByAccountNumber(accountNumber);

        if (!account.getOwner().getId().equals(user.getId())) {
            return "redirect:/dashboard";
        }

        model.addAttribute("account", account);
        model.addAttribute("transactions", transferService.history(account));
        return "transactions";
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
