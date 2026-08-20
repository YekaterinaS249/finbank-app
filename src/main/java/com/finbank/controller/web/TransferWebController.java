package com.finbank.controller.web;

import com.finbank.dto.TransferRequest;
import com.finbank.exception.BusinessRuleException;
import com.finbank.model.User;
import com.finbank.repository.UserRepository;
import com.finbank.service.AccountService;
import com.finbank.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class TransferWebController {

    private final TransferService transferService;
    private final AccountService accountService;
    private final UserRepository userRepository;

    public TransferWebController(TransferService transferService, AccountService accountService,
                                  UserRepository userRepository) {
        this.transferService = transferService;
        this.accountService = accountService;
        this.userRepository = userRepository;
    }

    @GetMapping("/transfer")
    public String transferForm(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("accounts", accountService.findByOwner(user));
        if (!model.containsAttribute("transferRequest")) {
            model.addAttribute("transferRequest", new TransferRequest());
        }
        return "transfer";
    }

    @PostMapping("/transfer")
    public String submitTransfer(@Valid @ModelAttribute("transferRequest") TransferRequest transferRequest,
                                  BindingResult bindingResult, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("accounts", accountService.findByOwner(user));

        if (bindingResult.hasErrors()) {
            return "transfer";
        }

        try {
            String ref = transferService.transfer(user, transferRequest);
            model.addAttribute("successMessage", "Transfer completed. Reference: " + ref);
            model.addAttribute("transferRequest", new TransferRequest());
        } catch (BusinessRuleException e) {
            model.addAttribute("transferError", e.getMessage());
        }

        return "transfer";
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
